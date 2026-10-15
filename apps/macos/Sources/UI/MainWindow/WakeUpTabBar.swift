import SwiftUI

/// Minimalist bottom navigation tab bar replicating WakeUp's 4 primary tabs: 日程, 课程, 学习, 我的.
public struct WakeUpTabBar: View {
    @Binding public var selectedTab: String
    public let showStudyTab: Bool
    public let onTabSelected: (String) -> Void

    public init(
        selectedTab: Binding<String>,
        showStudyTab: Bool = true,
        onTabSelected: @escaping (String) -> Void
    ) {
        self._selectedTab = selectedTab
        self.showStudyTab = showStudyTab
        self.onTabSelected = onTabSelected
    }

    private var tabs: [(id: String, name: String)] {
        if showStudyTab {
            return [
                ("schedule", "日程"),
                ("timetable", "课程"),
                ("study", "学习")
            ]
        } else {
            return [
                ("schedule", "日程"),
                ("timetable", "课程")
            ]
        }
    }

    public var body: some View {
        HStack(spacing: 50) {
            ForEach(tabs, id: \.id) { tab in
                let isSelected = (selectedTab == tab.id)

                Button(action: {
                    selectedTab = tab.id
                    onTabSelected(tab.id)
                }) {
                    VStack(spacing: 4) {
                        Text(tab.name)
                            .font(.system(size: 14, weight: isSelected ? .bold : .medium))
                            .foregroundColor(isSelected ? .primary : .secondary.opacity(0.8))

                        if isSelected {
                            Capsule()
                                .fill(Color.primary)
                                .frame(width: 14, height: 2.5)
                        } else {
                            Capsule()
                                .fill(Color.clear)
                                .frame(width: 14, height: 2.5)
                        }
                    }
                    .frame(height: 32)
                }
                .buttonStyle(.plain)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 6)
    }
}
