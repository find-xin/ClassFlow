import SwiftUI
import ClassFlowCore

/// Sheet replicating WakeUp's "设置时间表" screen with profile list, selection radio buttons, and creation entry.
public struct TimetableProfilesSheet: View {
    @Bindable public var appState: AppState
    @Binding public var isPresented: Bool

    @State private var profileToEdit: TimetableProfile? = nil
    @State private var isCreatingNew: Bool = false

    public init(appState: AppState, isPresented: Binding<Bool>) {
        self.appState = appState
        self._isPresented = isPresented
    }

    public var body: some View {
        VStack(spacing: 0) {
            // Header matching Image 1
            HStack {
                Button(action: { isPresented = false }) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(.primary)
                }
                .buttonStyle(.plain)

                Spacer()

                Text("设置时间表")
                    .font(.system(size: 16, weight: .bold))

                Spacer()

                Button("新建") {
                    createNewProfile()
                }
                .buttonStyle(.plain)
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(.white)
                .padding(.horizontal, 12)
                .padding(.vertical, 5)
                .background(Color(hex: "#FF5A5F"))
                .clipShape(Capsule())
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 14)
            .background(.bar)

            Divider()

            ScrollView {
                VStack(spacing: 12) {
                    ForEach(appState.timetableProfiles) { profile in
                        let isSelected = (profile.id == appState.activeProfileId)

                        HStack(alignment: .center) {
                            VStack(alignment: .leading, spacing: 4) {
                                HStack(spacing: 6) {
                                    Text(profile.name)
                                        .font(.system(size: 15, weight: .bold))
                                        .foregroundColor(.primary)

                                    Button(action: {
                                        profileToEdit = profile
                                    }) {
                                        Image(systemName: "pencil")
                                            .font(.system(size: 13))
                                            .foregroundColor(.secondary)
                                    }
                                    .buttonStyle(.plain)
                                }

                                Text("\(profile.sections.count) 节课")
                                    .font(.system(size: 12))
                                    .foregroundColor(.secondary)

                                Text(isSelected ? "当前课表在用" : "0个课表在用")
                                    .font(.system(size: 11))
                                    .foregroundColor(isSelected ? .secondary : .secondary.opacity(0.6))
                            }

                            Spacer()

                            // Radio Indicator
                            Button(action: {
                                withAnimation(.spring(response: 0.25, dampingFraction: 0.8)) {
                                    appState.selectTimetableProfile(id: profile.id)
                                }
                            }) {
                                if isSelected {
                                    ZStack {
                                        Circle()
                                            .fill(Color(hex: "#FF5A5F"))
                                            .frame(width: 22, height: 22)
                                        Image(systemName: "checkmark")
                                            .font(.system(size: 11, weight: .bold))
                                            .foregroundColor(.white)
                                    }
                                } else {
                                    Circle()
                                        .stroke(Color.secondary.opacity(0.35), lineWidth: 1.5)
                                        .frame(width: 22, height: 22)
                                }
                            }
                            .buttonStyle(.plain)
                        }
                        .padding(.horizontal, 18)
                        .padding(.vertical, 16)
                        .background(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .fill(Color(nsColor: .controlBackgroundColor))
                                .shadow(color: Color.black.opacity(0.04), radius: 4, y: 2)
                        )
                        .contentShape(Rectangle())
                        .onTapGesture {
                            withAnimation(.spring(response: 0.25, dampingFraction: 0.8)) {
                                appState.selectTimetableProfile(id: profile.id)
                            }
                        }
                    }
                }
                .padding(20)
            }
        }
        .frame(width: 480, height: 560)
        .sheet(item: $profileToEdit) { profile in
            TimetableProfileEditSheet(
                profile: profile,
                onSave: { updated in
                    appState.saveTimetableProfile(updated)
                    profileToEdit = nil
                },
                onDismiss: {
                    profileToEdit = nil
                }
            )
        }
    }

    private func createNewProfile() {
        let newProfile = TimetableProfile(
            name: "时间表 \(appState.timetableProfiles.count + 1)",
            sections: appState.currentSemester?.sections ?? TimetableProfile.standardDefault().sections,
            fixedDuration: true,
            sectionDurationMinutes: 45
        )
        profileToEdit = newProfile
    }
}
