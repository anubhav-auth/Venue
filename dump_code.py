#!/usr/bin/env python3

"""
Interactive menu-driven code dump utility
Run without arguments for interactive mode, or use CLI args as before
"""

import os
import sys
import shutil
import subprocess
import pathlib
import argparse
from typing import List, Set, Dict, Optional
from collections import defaultdict

# Try to import inquirer for interactive menu
try:
    import inquirer
    INTERACTIVE_AVAILABLE = True
except ImportError:
    INTERACTIVE_AVAILABLE = False

def parse_args():
    parser = argparse.ArgumentParser(
        description="Dump repository code optimized for LLM context"
    )
    parser.add_argument(
        "-o", "--output",
        default=None,
        help="Output file name"
    )
    parser.add_argument(
        "-f", "--folders",
        nargs="*",
        help="Specific folders to include"
    )
    parser.add_argument(
        "--full-tree",
        action="store_true",
        help="Show full project tree"
    )
    parser.add_argument(
        "--force",
        nargs="*",
        help="Force-include specific files even if gitignored"
    )
    parser.add_argument(
        "--format",
        choices=["xml", "markdown", "plain"],
        default=None,
        help="Output format (default: xml, best for Claude/Gemini)"
    )
    parser.add_argument(
        "--interactive", "-i",
        action="store_true",
        help="Force interactive mode"
    )
    return parser.parse_args()

def get_language(path: pathlib.Path) -> str:
    """Detect language from file extension"""
    ext_map = {
        ".py": "python", ".js": "javascript", ".ts": "typescript",
        ".jsx": "jsx", ".tsx": "tsx", ".java": "java",
        ".cpp": "cpp", ".c": "c", ".h": "c", ".hpp": "cpp",
        ".go": "go", ".rs": "rust", ".rb": "ruby",
        ".php": "php", ".swift": "swift", ".kt": "kotlin",
        ".scala": "scala", ".sh": "bash", ".bash": "bash",
        ".sql": "sql", ".html": "html", ".css": "css",
        ".json": "json", ".xml": "xml", ".yaml": "yaml", ".yml": "yaml",
        ".md": "markdown", ".txt": "text", ".env": "env",
        ".dockerfile": "dockerfile", ".gitignore": "gitignore"
    }
    ext = path.suffix.lower()
    if ext in ext_map:
        return ext_map[ext]
    # Check filename patterns
    name = path.name.lower()
    if name == "dockerfile":
        return "dockerfile"
    if name == "makefile":
        return "makefile"
    return "text"

def count_lines(path: pathlib.Path) -> int:
    """Count lines in file"""
    try:
        return len(path.read_text(encoding="utf-8", errors="ignore").splitlines())
    except Exception:
        return 0

def in_git_repo(root: pathlib.Path) -> bool:
    return (root / ".git").exists()

def get_files_via_git(root: pathlib.Path) -> List[pathlib.Path]:
    cmd = ["git", "ls-files", "-z", "-co", "--exclude-standard"]
    out = subprocess.check_output(cmd, cwd=root)
    items = [p for p in out.decode("utf-8", "replace").split("\0") if p]
    return [root / p for p in items]

def get_files_via_lib(root: pathlib.Path) -> List[pathlib.Path]:
    gi = root / ".gitignore"
    if gi.exists():
        try:
            from gitignore_parser import parse_gitignore
            matches = parse_gitignore(str(gi))
            paths = []
            for dirpath, dirnames, filenames in os.walk(root):
                if os.path.basename(dirpath) == ".git":
                    dirnames[:] = []
                    continue
                for name in filenames:
                    fp = pathlib.Path(dirpath) / name
                    if matches(str(fp)):
                        continue
                    paths.append(fp)
            return [p for p in paths if p.is_file()]
        except Exception:
            pass
        try:
            from pathspec import PathSpec
            patterns = gi.read_text(encoding="utf-8", errors="ignore").splitlines()
            spec = PathSpec.from_lines("gitwildmatch", patterns)
            paths = []
            for dirpath, dirnames, filenames in os.walk(root):
                if os.path.basename(dirpath) == ".git":
                    dirnames[:] = []
                    continue
                for name in filenames:
                    rel = os.path.relpath(os.path.join(dirpath, name), start=root)
                    if spec.match_file(rel):
                        continue
                    paths.append(root / rel)
            return [p for p in paths if p.is_file()]
        except Exception:
            pass
    skip_dirs = {".git", ".hg", ".svn", "node_modules", ".venv", "venv", "__pycache__"}
    paths = []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in skip_dirs]
        for name in filenames:
            paths.append(pathlib.Path(dirpath) / name)
    return [p for p in paths if p.is_file()]

def filter_by_folders(files: List[pathlib.Path], root: pathlib.Path,
                     folders: List[str]) -> List[pathlib.Path]:
    if not folders:
        return files
    folder_paths = {pathlib.Path(f).as_posix() for f in folders}
    filtered = []
    for file in files:
        rel = file.relative_to(root).as_posix()
        for folder in folder_paths:
            if rel.startswith(folder + "/") or rel.split("/")[0] == folder:
                filtered.append(file)
                break
    return filtered

def add_forced_files(files: List[pathlib.Path], root: pathlib.Path,
                    forced: List[str]) -> List[pathlib.Path]:
    if not forced:
        return files
    existing_rels = {f.relative_to(root).as_posix() for f in files}
    all_files = list(files)
    for forced_path in forced:
        fp = root / forced_path
        if fp.exists() and fp.is_file():
            rel = fp.relative_to(root).as_posix()
            if rel not in existing_rels:
                all_files.append(fp)
    return all_files

def is_binary_file(path: pathlib.Path, blocksize: int = 1024) -> bool:
    try:
        with open(path, "rb") as f:
            chunk = f.read(blocksize)
            return b"\x00" in chunk
    except Exception:
        return True

def build_tree(paths: List[pathlib.Path], root: pathlib.Path) -> str:
    rels = sorted([p.relative_to(root).as_posix() for p in paths])
    dirs = defaultdict(set)
    files_in_dir = defaultdict(list)
    for rel in rels:
        parts = rel.split("/")
        for i in range(len(parts) - 1):
            parent = "/".join(parts[: i + 1])
            child = parts[i + 1]
            if i + 1 < len(parts) - 1:
                dirs[parent].add(child)
        dkey = "/".join(parts[:-1])
        files_in_dir[dkey].append(parts[-1])
    dirs[""] |= set([p.split("/")[0] for p in rels if "/" in p])
    files_in_dir[""] += [p for p in rels if "/" not in p]
    lines = [root.name + "/"]
    def render(dir_key: str, prefix: str = ""):
        entries = sorted([("DIR", n) for n in sorted(dirs.get(dir_key, []))] +
                        [("FILE", n) for n in sorted(files_in_dir.get(dir_key, []))])
        for idx, (kind, name) in enumerate(entries):
            last = idx == len(entries) - 1
            connector = "└── " if last else "├── "
            lines.append(prefix + connector + name)
            if kind == "DIR":
                next_key = name if dir_key == "" else f"{dir_key}/{name}"
                extension = "    " if last else "│   "
                render(next_key, prefix + extension)
    render("")
    return "\n".join(lines)

def write_xml_format(out, root: pathlib.Path, tree_files: List[pathlib.Path],
                    content_files: List[pathlib.Path], folders: Optional[List[str]]):
    """XML format - optimized for Claude"""
    out.write("<codebase>\n\n")
    # Metadata section
    out.write("<metadata>\n")
    out.write(f"  <project_name>{root.name}</project_name>\n")
    out.write(f"  <file_count>{len(content_files)}</file_count>\n")
    if folders:
        out.write(f"  <filtered_folders>{', '.join(folders)}</filtered_folders>\n")
    out.write("</metadata>\n\n")
    # Tree structure
    out.write("<tree>\n")
    out.write(build_tree(tree_files, root))
    out.write("\n</tree>\n\n")
    # Group files by directory
    out.write("<files>\n\n")
    for path in sorted(content_files, key=lambda p: p.as_posix()):
        rel = path.relative_to(root).as_posix()
        lang = get_language(path)
        lines = count_lines(path)
        out.write(f"<file path=\"{rel}\">\n")
        out.write(f"  <language>{lang}</language>\n")
        out.write(f"  <lines>{lines}</lines>\n")
        out.write(f"  <content>\n")
        if is_binary_file(path):
            out.write("[binary file]\n")
        else:
            try:
                text = path.read_text(encoding="utf-8")
            except UnicodeDecodeError:
                text = path.read_text(encoding="utf-8", errors="replace")
            out.write(text)
            if not text.endswith("\n"):
                out.write("\n")
        out.write("  </content>\n")
        out.write("</file>\n\n")
    out.write("</files>\n\n")
    out.write("</codebase>\n")

def write_markdown_format(out, root: pathlib.Path, tree_files: List[pathlib.Path],
                         content_files: List[pathlib.Path], folders: Optional[List[str]]):
    """Markdown format - good for general LLMs"""
    out.write("# Codebase Context\n\n")
    # Metadata
    out.write("## Metadata\n\n")
    out.write(f"- **Project**: {root.name}\n")
    out.write(f"- **Total Files**: {len(content_files)}\n")
    if folders:
        out.write(f"- **Filtered Folders**: {', '.join(folders)}\n")
    out.write("\n")
    # Tree
    out.write("## Directory Structure\n\n")
    out.write("```\n")
    out.write(build_tree(tree_files, root))
    out.write("\n```\n\n")
    # Files
    out.write("## Source Files\n\n")
    for path in sorted(content_files, key=lambda p: p.as_posix()):
        rel = path.relative_to(root).as_posix()
        lang = get_language(path)
        lines = count_lines(path)
        out.write(f"### {rel}\n\n")
        out.write(f"**Language**: {lang} | **Lines**: {lines}\n\n")
        if is_binary_file(path):
            out.write("```\n[binary file]\n```\n\n")
        else:
            try:
                text = path.read_text(encoding="utf-8")
            except UnicodeDecodeError:
                text = path.read_text(encoding="utf-8", errors="replace")
            out.write(f"```{lang}\n")
            out.write(text)
            if not text.endswith("\n"):
                out.write("\n")
            out.write("```\n\n")

def write_plain_format(out, root: pathlib.Path, tree_files: List[pathlib.Path],
                      content_files: List[pathlib.Path], folders: Optional[List[str]]):
    """Plain text format - original style"""
    out.write("PROJECT TREE\n")
    out.write("============\n")
    out.write(build_tree(tree_files, root))
    out.write("\n\n")
    out.write("FILES AND CONTENTS\n")
    out.write("==================\n\n")
    for path in sorted(content_files, key=lambda p: p.as_posix()):
        rel = path.relative_to(root).as_posix()
        out.write(f"--- FILE: {rel} ---\n")
        if is_binary_file(path):
            out.write("[binary file skipped]\n\n")
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            text = path.read_text(encoding="utf-8", errors="replace")
        out.write(text)
        if not text.endswith("\n"):
            out.write("\n")
        out.write("\n")

def get_top_level_folders(root: pathlib.Path) -> List[str]:
    """Get top-level directories in the repo"""
    folders = []
    skip_dirs = {".git", ".hg", ".svn", "__pycache__", ".venv", "venv", "node_modules"}
    for item in root.iterdir():
        if item.is_dir() and item.name not in skip_dirs:
            folders.append(item.name)
    return sorted(folders)

def interactive_menu(root: pathlib.Path) -> dict:
    """Show interactive menu and return configuration"""
    if not INTERACTIVE_AVAILABLE:
        print("❌ Interactive mode requires 'inquirer' package")
        print("   Install with: pip install inquirer")
        sys.exit(1)

    print("\n🚀 Interactive Code Dump Configuration\n")

    questions = []

    # Get available folders
    available_folders = get_top_level_folders(root)

    # Folder selection
    if available_folders:
        questions.append(
            inquirer.Checkbox(
                'folders',
                message="Select folders to include (space to select, enter to continue, or select none for all)",
                choices=available_folders,
            )
        )

    # Format selection
    questions.append(
        inquirer.List(
            'format',
            message="Select output format",
            choices=[
                ('XML (Best for Claude/Gemini)', 'xml'),
                ('Markdown (Universal, human-readable)', 'markdown'),
                ('Plain Text (Simple format)', 'plain'),
            ],
        )
    )

    # Full tree option
    questions.append(
        inquirer.Confirm(
            'full_tree',
            message="Show full project tree (even if filtering folders)?",
            default=False
        )
    )

    # Output filename
    questions.append(
        inquirer.Text(
            'output',
            message="Output filename",
            default="codebase_context.txt"
        )
    )

    # Force include files
    questions.append(
        inquirer.Text(
            'force_files',
            message="Force-include files (comma-separated, optional)",
            default=""
        )
    )

    answers = inquirer.prompt(questions)

    if answers is None:
        print("\n❌ Cancelled")
        sys.exit(0)

    # Process answers
    config = {
        'folders': answers.get('folders', []),
        'format': answers['format'],
        'full_tree': answers['full_tree'],
        'output': answers['output'],
        'force': [f.strip() for f in answers['force_files'].split(',') if f.strip()]
    }

    # Show summary
    print("\n📋 Configuration Summary:")
    print(f"   Output: {config['output']}")
    print(f"   Format: {config['format']}")
    if config['folders']:
        print(f"   Folders: {', '.join(config['folders'])}")
    else:
        print(f"   Folders: All (entire repository)")
    if config['full_tree']:
        print(f"   Tree: Full project tree")
    if config['force']:
        print(f"   Forced files: {', '.join(config['force'])}")
    print()

    return config

def main():
    args = parse_args()
    root = pathlib.Path.cwd()

    # Determine if we should use interactive mode
    use_interactive = args.interactive or (
        INTERACTIVE_AVAILABLE and 
        args.output is None and 
        args.folders is None and 
        args.format is None
    )

    if use_interactive:
        config = interactive_menu(root)
        output_file = config['output']
        folders = config['folders'] if config['folders'] else None
        full_tree = config['full_tree']
        force_files = config['force'] if config['force'] else None
        output_format = config['format']
    else:
        output_file = args.output or "codebase_context.txt"
        folders = args.folders
        full_tree = args.full_tree
        force_files = args.force
        output_format = args.format or "xml"

    out_path = root / output_file

    # Gather files
    if shutil.which("git") and in_git_repo(root):
        try:
            all_files = get_files_via_git(root)
        except Exception:
            all_files = get_files_via_lib(root)
    else:
        all_files = get_files_via_lib(root)

    all_files = [p for p in all_files if p.resolve() != out_path.resolve()]
    all_files = [p for p in all_files if p.is_file()]

    content_files = filter_by_folders(all_files, root, folders or [])
    content_files = add_forced_files(content_files, root, force_files or [])
    tree_files = all_files if full_tree else content_files

    # Write output
    with open(out_path, "w", encoding="utf-8") as out:
        if output_format == "xml":
            write_xml_format(out, root, tree_files, content_files, folders)
        elif output_format == "markdown":
            write_markdown_format(out, root, tree_files, content_files, folders)
        else:
            write_plain_format(out, root, tree_files, content_files, folders)

    print(f"✓ Wrote {out_path}")
    print(f"  Format: {output_format}")
    print(f"  Files: {len(content_files)}")
    total_lines = sum(count_lines(f) for f in content_files if not is_binary_file(f))
    print(f"  Lines: ~{total_lines:,}")

if __name__ == "__main__":
    main()