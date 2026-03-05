import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import path from 'path';
export default defineConfig(function (_a) {
    var mode = _a.mode;
    var env = loadEnv(mode, process.cwd(), '');
    return {
        define: { global: 'globalThis' },
        plugins: [react(), tailwindcss()],
        resolve: { alias: { '@': path.resolve(__dirname, './src') } },
        server: {
            proxy: {
                '/api': { target: env.VITE_API_PROXY_TARGET || 'http://localhost:8080', changeOrigin: true },
                '/ws': { target: env.VITE_WS_PROXY_TARGET || 'http://localhost:8080', ws: true, changeOrigin: true },
            },
        },
    };
});
