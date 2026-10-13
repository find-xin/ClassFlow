#!/usr/bin/env python3
"""
ClassFlow Foundation Test Suite
Validates:
1. Canonical JSON Schemas & Fixtures
2. Core Reference Algorithms:
   - Semester week calculations
   - Odd / Even / Custom week pattern filtering
   - ScheduleException resolution (cancellation, rescheduling)
   - Non-blocking conflict detection
"""

import json
import os
import sys
import datetime
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent
SCHEMAS_DIR = ROOT_DIR / "packages" / "schemas"
FIXTURES_DIR = ROOT_DIR / "packages" / "fixtures"

def log(msg, status="INFO"):
    print(f"[{status}] {msg}")

def test_json_syntax():
    """Verify all schema and fixture JSON files are syntactically valid."""
    log("Verifying JSON syntax of schemas and fixtures...")
    json_files = list(SCHEMAS_DIR.glob("*.json")) + list(FIXTURES_DIR.glob("*.json"))
    assert len(json_files) > 0, "No JSON files found!"
    for f in json_files:
        with open(f, "r", encoding="utf-8") as fp:
            try:
                data = json.load(fp)
                assert isinstance(data, (dict, list))
            except Exception as e:
                raise AssertionError(f"Invalid JSON in {f.name}: {e}")
    log(f"Verified {len(json_files)} JSON files successfully.", "PASS")

def calculate_week(semester_start_date_str: str, target_date_str: str) -> int:
    """Calculate 1-indexed teaching week from Monday of first teaching week."""
    start_date = datetime.date.fromisoformat(semester_start_date_str)
    target_date = datetime.date.fromisoformat(target_date_str)
    # Ensure start_date is Monday
    start_monday = start_date - datetime.timedelta(days=start_date.weekday())
    target_monday = target_date - datetime.timedelta(days=target_date.weekday())
    diff_days = (target_monday - start_monday).days
    return (diff_days // 7) + 1

def is_week_active(pattern_type: str, custom_weeks: list, week_num: int) -> bool:
    """Check if week pattern is active for a given week."""
    if pattern_type == "all":
        return True
    elif pattern_type == "odd":
        return week_num % 2 == 1
    elif pattern_type == "even":
        return week_num % 2 == 0
    elif pattern_type == "custom":
        return week_num in (custom_weeks or [])
    return False

def test_week_calculation_algorithm():
    """Verify teaching week arithmetic across various dates."""
    log("Testing semester teaching week calculation algorithm...")
    # Semester starts Monday Sep 7, 2026
    start = "2026-09-07"
    assert calculate_week(start, "2026-09-07") == 1
    assert calculate_week(start, "2026-09-13") == 1 # Sunday of week 1
    assert calculate_week(start, "2026-09-14") == 2
    assert calculate_week(start, "2026-10-19") == 7
    assert calculate_week(start, "2026-10-25") == 7
    assert calculate_week(start, "2026-10-26") == 8
    log("Week calculation algorithm passed.", "PASS")

def test_week_pattern_filtering():
    """Verify odd, even, all, and custom week pattern evaluation."""
    log("Testing week pattern filtering rules...")
    assert is_week_active("all", None, 1) is True
    assert is_week_active("all", None, 2) is True
    
    assert is_week_active("odd", None, 1) is True
    assert is_week_active("odd", None, 2) is False
    assert is_week_active("odd", None, 3) is True

    assert is_week_active("even", None, 1) is False
    assert is_week_active("even", None, 2) is True
    assert is_week_active("even", None, 4) is True

    custom = [1, 2, 3, 5, 7, 8, 9, 10]
    assert is_week_active("custom", custom, 3) is True
    assert is_week_active("custom", custom, 4) is False
    assert is_week_active("custom", custom, 5) is True
    log("Week pattern filtering rules passed.", "PASS")

def test_timeline_occurrence_projection_with_exceptions():
    """Verify projection of course schedules into occurrences and exception resolution."""
    log("Testing occurrence projection and ScheduleException resolution...")
    with open(FIXTURES_DIR / "sample_semester.json") as f:
        semester = json.load(f)
    with open(FIXTURES_DIR / "sample_course.json") as f:
        course = json.load(f)
    with open(FIXTURES_DIR / "sample_exception.json") as f:
        exception = json.load(f)

    # Base schedule 1: Monday (dayOfWeek=1), sections 1-2
    sched1 = course["schedules"][0]
    
    # Oct 19, 2026 is Monday of week 7
    target_date = "2026-10-19"
    week_num = calculate_week(semester["startDate"], target_date)
    assert week_num == 7

    # Exception specifies rescheduling from Oct 19 to Oct 24 (Saturday, sections 5-6)
    occurrences = []
    if exception["originalDate"] == target_date and exception["exceptionType"] == "reschedule":
        # Original is replaced
        occurrences.append({
            "id": f"course_occ_{sched1['id']}_{exception['targetDate']}",
            "courseId": course["id"],
            "title": course["name"],
            "date": exception["targetDate"],
            "dayOfWeek": exception["targetDayOfWeek"],
            "startSection": exception["targetStartSection"],
            "endSection": exception["targetEndSection"],
            "classroom": exception["targetClassroom"],
            "status": "rescheduled"
        })
    
    assert len(occurrences) == 1
    occ = occurrences[0]
    assert occ["date"] == "2026-10-24"
    assert occ["dayOfWeek"] == 6
    assert occ["startSection"] == 5
    assert occ["endSection"] == 6
    assert occ["classroom"] == "东九楼 D108"
    assert occ["status"] == "rescheduled"
    log("ScheduleException resolution passed.", "PASS")

def test_conflict_detection_algorithm():
    """Verify non-blocking detection of overlapping time slots."""
    log("Testing conflict detection algorithm...")
    
    items = [
        {
            "id": "item1",
            "date": "2026-10-20",
            "startSection": 1,
            "endSection": 2,
            "title": "高等数学"
        },
        {
            "id": "item2",
            "date": "2026-10-20",
            "startSection": 2,
            "endSection": 3,
            "title": "大学物理 (选修冲突)"
        },
        {
            "id": "item3",
            "date": "2026-10-20",
            "startSection": 4,
            "endSection": 5,
            "title": "体育课 (无冲突)"
        }
    ]

    conflicts = []
    for i in range(len(items)):
        for j in range(i + 1, len(items)):
            a, b = items[i], items[j]
            if a["date"] == b["date"]:
                # Section overlap check: max(start) <= min(end)
                overlap = max(a["startSection"], b["startSection"]) <= min(a["endSection"], b["endSection"])
                if overlap:
                    conflicts.append((a["id"], b["id"]))

    assert len(conflicts) == 1
    assert conflicts[0] == ("item1", "item2")
    log("Conflict detection algorithm passed (identified 1 overlap cleanly).", "PASS")

def main():
    print("=" * 60)
    print("Starting ClassFlow Phase 0/1 Foundation Test Suite")
    print("=" * 60)
    try:
        test_json_syntax()
        test_week_calculation_algorithm()
        test_week_pattern_filtering()
        test_timeline_occurrence_projection_with_exceptions()
        test_conflict_detection_algorithm()
        print("=" * 60)
        print("ALL FOUNDATION TESTS PASSED (5/5 suites successful).")
        print("=" * 60)
        return 0
    except Exception as e:
        print(f"\n[FAILURE] {e}", file=sys.stderr)
        return 1

if __name__ == "__main__":
    sys.exit(main())
