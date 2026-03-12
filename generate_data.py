import csv
import random

# Degrees
valid_degrees = ["BTECH", "BCA", "MTECH", "MCA", "MSC"]

def random_phone():
    return "9" + "".join(str(random.randint(0, 9)) for _ in range(9))

def generate_students(prefix, start_idx, count):
    records = []
    for i in range(start_idx, start_idx + count):
        name = f"{prefix} Student {i}"
        regno = f"{prefix.lower()}{i:05d}"
        email = f"{regno}@example.com"
        degree = random.choice(valid_degrees)
        contact = random_phone()
        year = random.choice([2024, 2025, 2026, 2027])
        records.append([name, regno, email, degree, contact, year])
    return records

def write_csv(filename, header, data):
    with open(filename, 'w', newline='') as f:
        writer = csv.writer(f)
        writer.writerow(header)
        writer.writerows(data)

# 1. 50 Volunteers
vol_data = generate_students("VOL", 1, 50)
write_csv("volunteers.csv", ["Name", "Regdno", "Emailid", "Degree", "Contactno", "Passoutyear"], vol_data)

# 2. Audience Data: total 2000 students (day 1 and day 2 overlapping or separate? separate is easiest)
# "4 csv for audiece day 1 and 4csv for audience day 2"
# Meaning 1000 for Day 1 (in 4 parts of 250) and 1000 for Day 2 (in 4 parts of 250)
# So total 2000 audience.
day1_data = generate_students("AUD1", 1, 1000)
day2_data = generate_students("AUD2", 1, 1000)

for i in range(4):
    part_data = day1_data[i*250 : (i+1)*250]
    write_csv(f"audience_day1_part{i+1}.csv", ["Name", "Regdno", "Emailid", "Degree", "Contactno", "Passoutyear"], part_data)

for i in range(4):
    part_data = day2_data[i*250 : (i+1)*250]
    write_csv(f"audience_day2_part{i+1}.csv", ["Name", "Regdno", "Emailid", "Degree", "Contactno", "Passoutyear"], part_data)

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
