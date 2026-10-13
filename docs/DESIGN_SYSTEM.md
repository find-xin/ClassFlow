# ClassFlow Design System & UI/UX Guidelines

**Version**: 1.0.0 | **Phase**: 1 | **Status**: Active Design

---

## 1. Design Philosophy: Native First & Content-Centric

ClassFlow is engineered to showcase the student's timetable with maximum visual clarity and minimum interface chrome.

```
+---------------------------------------------------------------------------------+
|  [Traffic Lights]   [ 2026-2027 秋季学期 v ]    [<] [ 第8周 (当前周) ] [>]  [今]    [+] [导入] [...] |  <-- Native Toolbar
+----+----------------------------------------------------------------------------+
|节次| 周一 10/19 | 周二 10/20 | 周三 10/21 | 周四 10/22 | 周五 10/23 | 周六 10/24 | 周日 10/25 |  <-- Week Header
+----+------------+------------+------------+------------+------------+------------+------------+
| 1  | [高等数学] |            | [大学物理] |            | [高等数学] |            |            |  <-- 7-Column Grid
| 2  | D102       |            | A201       |            | D102       |            |            |
+----+------------+------------+------------+------------+------------+------------+------------+
| 3  |            | [程序设计] |            | [线性代数] |            |            |            |
| 4  |            | 机房302    |            | B104       |            |            |            |
+----+----------------------------------------------------------------------------+
```

### Core Layout Rules
1. **Full-Width Timetable Grid**: No permanent 3-column navigation sidebar occupying horizontal screen real estate. The 7-column timetable canvas is the primary hero view.
2. **Left Section Axis**: Displays period numbers (1, 2, 3...) along with start and end times in subtle secondary typography.
3. **Card Transparency & Blur**: Course cards feature modern, semi-transparent frosted glass treatments against subtle canvas tints or user-selected custom background wallpapers.
4. **Non-Current Week Dimming**: Courses that do not occur in the currently selected week are either hidden or displayed with a dim translucent outline (user-configurable).
5. **No Proprietary Asset Replication**: ClassFlow utilizes 100% original vectors, SF Symbols on macOS, Material Icons on Android, and open typography.

---

## 2. Design Tokens

### 2.1 Color Palette
ClassFlow features a calibrated 12-color semantic course palette designed for high contrast and accessible text legibility:

| Color Name | Hex Code | Purpose | Text Contrast Pairing |
| :--- | :--- | :--- | :--- |
| **Indigo Slate** | `#4F46E5` | STEM & Mathematics | `#FFFFFF` |
| **Teal Mint** | `#0D9488` | Natural Sciences & Biology | `#FFFFFF` |
| **Sky Azure** | `#0284C7` | Computer Science & Logic | `#FFFFFF` |
| **Rose Coral** | `#E11D48` | Languages & Literature | `#FFFFFF` |
| **Amber Gold** | `#D97706` | Social Sciences & History | `#FFFFFF` |
| **Emerald Pine**| `#059669` | Labs & Engineering | `#FFFFFF` |
| **Violet Iris** | `#7C3AED` | Arts & Humanities | `#FFFFFF` |
| **Fuchsia Plum**| `#C026D3` | Philosophy & Ethics | `#FFFFFF` |
| **Cyan Breeze** | `#0891B2` | Economics & Business | `#FFFFFF` |
| **Orange Rust** | `#EA580C` | Physical Education & Sports | `#FFFFFF` |
| **Stone Gray** | `#57534E` | Non-academic / Meetings | `#FFFFFF` |
| **Neutral Dim** | `#94A3B8` | Non-current week placeholder| `#0F172A` |

### 2.2 Typography
- **macOS**: San Francisco (SF Pro, SF Pro Rounded for section headers).
- **Android / HyperOS**: MiSans on Xiaomi HyperOS devices; Roboto / Google Sans on standard Android devices.
- **Scale**:
  - Grid Day Title: `14pt` Semibold / Medium
  - Grid Date Subtitle: `11pt` Regular (Secondary)
  - Section Period Number: `12pt` Bold
  - Section Time Subtitle: `9pt` Monospace / Tabular digits
  - Course Card Title: `13pt` Semibold, max 2 lines with smart ellipsis
  - Course Card Location: `11pt` Regular, max 2 lines

---

## 3. macOS 15 (Sequoia) Interaction Specifications

### 3.1 Primary Window & Toolbar
- Native unified title bar with system window buttons (traffic lights).
- Custom window toolbar:
  - **Semester PopUpButton**: Fast switching between current and historical semesters.
  - **Week Navigation Segment**: Prev `[<]`, Current Week Picker Sheet `[ 第 8 周 ]`, Next `[>]`.
  - **Today Button**: Jumps back to current week and flashes today's column.
  - **Add Course Button**: Primary action `+` opening native creation sheet.
  - **Action Menu `...`**: Export, Import, Settings, Show/Hide Weekends.
- **Non-modal Popover**: Clicking a course card summons a native AppKit/SwiftUI Popover anchored directly to the card bounding rect, displaying teacher, location, week pattern, and edit options.
- **Keyboard Shortcuts**:
  - `Cmd + T`: Jump to Today.
  - `Cmd + Left / Right`: Previous / Next week.
  - `Cmd + N`: Add new course.
  - `Cmd + ,`: Open Settings window.

### 3.2 Menu Bar Companion (ClassFlow Menu Extra)
- Sits unobtrusively in the macOS menu bar.
- Shows current class or upcoming countdown: `高等数学 10:00 (D102)`.
- Pulls down a compact mini-schedule for today with a single-click shortcut to raise the main window.

---

## 4. Android / HyperOS Interaction Specifications

### 4.1 Navigation Architecture
ClassFlow Android utilizes an intuitive 3-tab bottom navigation structure:

```
[  课程表 (Timetable)  ]    [  日程 (Agenda)  ]    [  我的 (Settings/Sync)  ]
```

1. **课程表 (Timetable)**:
   - Full-bleed 7-column grid with swipe gestures for previous/next week navigation.
   - Collapsible top bar showing current week dropdown and today indicator.
2. **日程 (Agenda)**:
   - Chronological vertical feed of today's and upcoming classes.
   - Pre-engineered to seamlessly ingest generic calendar events in Phase 2+.
3. **我的 (Profile & Settings)**:
   - Semester management, BYOC Cloud Sync status & setup, Backup/Export, Theme and Notification settings.

### 4.2 Bottom Sheet Course Inspection
- Tapping a course card expands a smooth Material 3 modal bottom sheet.
- Displays full course details, classroom directions, and action buttons:
  - **编辑课程 (Edit Course)**: Prompts for Editing Scope (This Only / This & Future / All).
  - **临时调课 / 停课 (Reschedule / Cancel)**: Direct shortcuts to insert `ScheduleException`.
  - **删除课程 (Delete)**.

---

## 5. Desktop & Lockscreen Widgets

| Size | macOS WidgetKit | Android Glance / AppWidget | Content |
| :--- | :--- | :--- | :--- |
| **Small (2×2)** | Next Class | Next Class | Course name, room, start time countdown, progress ring |
| **Medium (4×2)**| Today's Schedule | Today's Schedule | Timeline list of 3-4 classes for the current day |
| **Large (4×4)** | Week Overview | Week Overview | Compact 7-column or 5-column mini timetable grid |
