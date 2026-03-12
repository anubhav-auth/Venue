import csv
import random

# Branches
valid_branches = ["CSE", "IT"]

def generate_students(prefix, start_idx, count):
    records = []
    for i in range(start_idx, start_idx + count):
        full_name = f"{prefix} Student {i}"
        regno = f"{prefix.lower()}{i:05d}"
        branch = random.choice(valid_branches)
        records.append([regno, full_name, branch])
    return records

def write_csv(filename, header, data):
    with open(filename, 'w', newline='') as f:
        writer = csv.writer(f)
        writer.writerow(header)
        writer.writerows(data)

# CSV Header
csv_header = ["Regd. No", "Full Name", "BRANCH"]

# 1. 50 Volunteers
vol_data = generate_students("VOL", 1, 50)
write_csv("volunteers.csv", csv_header, vol_data)

# 2. Audience Data: total 2000 students per day
# 1 file with 1100, 3 files with 300 each (to match rooms 1100, 300, 300, 300)
day1_data = generate_students("AUD1", 1, 2000)
day2_data = generate_students("AUD2", 1, 2000)

distribution = [1100, 300, 300, 300]

# Day 1
start = 0
for i, count in enumerate(distribution):
    part_data = day1_data[start : start+count]
    write_csv(f"audience_day1_part{i+1}.csv", csv_header, part_data)
    start += count

# Day 2
start = 0
for i, count in enumerate(distribution):
    part_data = day2_data[start : start+count]
    write_csv(f"audience_day2_part{i+1}.csv", csv_header, part_data)
    start += count

# 3. Rooms: 4 rooms across both days. 
# 1 room (1100 cap, 30 rows), 3 rooms (300 cap, 30 rows)
room_header = ["RoomName", "Capacity", "Building", "Floor", "Day", "SeatsPerRow"]
room_data = []
for day in ["day1", "day2"]:
    room_data.append(["Main Hall", 1100, "Block A", "Ground", day, 30])
    room_data.append(["Room 201", 300, "Block B", "2", day, 30])
    room_data.append(["Room 202", 300, "Block B", "2", day, 30])
    room_data.append(["Room 203", 300, "Block B", "2", day, 30])

write_csv("rooms.csv", room_header, room_data)

print("Data generation complete.")
