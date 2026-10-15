import SwiftUI

/// A native macOS autocomplete text field with dynamic search suggestions.
/// Matches WakeUp 课程表's intelligent autocompletion:
/// - Clicking/focusing into the field displays existing candidates below it.
/// - Typing text narrows down matching items in real time.
/// - Selecting an item fills the text field immediately.
/// - Typing custom/unlisted text is fully supported without restriction.
public struct SearchSuggestionTextField: View {
    public let placeholder: String
    @Binding public var text: String
    public let suggestions: [String]
    public var icon: String? = nil
    public var hasContainer: Bool = false
    public var onCommit: (() -> Void)? = nil

    @FocusState private var isFieldFocused: Bool
    @State private var isDropdownPresented: Bool = false
    @State private var hoveredItem: String? = nil
    @State private var fieldSize: CGSize = .zero

    public init(
        placeholder: String,
        text: Binding<String>,
        suggestions: [String],
        icon: String? = nil,
        hasContainer: Bool = false,
        onCommit: (() -> Void)? = nil
    ) {
        self.placeholder = placeholder
        self._text = text
        self.suggestions = suggestions
        self.icon = icon
        self.hasContainer = hasContainer
        self.onCommit = onCommit
    }

    private var filteredSuggestions: [String] {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            return suggestions
        }
        return suggestions.filter { candidate in
            candidate.localizedCaseInsensitiveContains(trimmed)
        }
    }

    public var body: some View {
        HStack(spacing: 6) {
            if let icon = icon, hasContainer {
                Image(systemName: icon)
                    .foregroundColor(.secondary)
                    .font(.system(size: 12))
            }

            TextField(placeholder, text: $text)
                .textFieldStyle(.plain)
                .focused($isFieldFocused)
                .onSubmit {
                    isDropdownPresented = false
                    onCommit?()
                }

            // Quick clear button
            if !text.isEmpty {
                Button(action: {
                    text = ""
                    if !suggestions.isEmpty {
                        isDropdownPresented = true
                    }
                }) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.secondary.opacity(0.5))
                        .font(.system(size: 11))
                }
                .buttonStyle(.plain)
                .help("清空")
            }

            // Dropdown indicator toggle button
            if !suggestions.isEmpty {
                Button(action: {
                    withAnimation(.easeInOut(duration: 0.15)) {
                        isDropdownPresented.toggle()
                    }
                    if isDropdownPresented {
                        isFieldFocused = true
                    }
                }) {
                    Image(systemName: "chevron.down")
                        .foregroundColor(.secondary.opacity(0.6))
                        .font(.system(size: 10, weight: .semibold))
                        .rotationEffect(.degrees(isDropdownPresented && !filteredSuggestions.isEmpty ? 180 : 0))
                }
                .buttonStyle(.plain)
                .help("已有记录选择")
            }
        }
        .padding(.horizontal, hasContainer ? 10 : 0)
        .padding(.vertical, hasContainer ? 7 : 0)
        .background(
            GeometryReader { geo in
                Color.clear
                    .onAppear { fieldSize = geo.size }
                    .onChange(of: geo.size) { _, newSize in fieldSize = newSize }
            }
        )
        .background(
            Group {
                if hasContainer {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(Color(nsColor: .textBackgroundColor).opacity(0.6))
                }
            }
        )
        .onChange(of: isFieldFocused) { _, focused in
            if focused {
                if !suggestions.isEmpty {
                    withAnimation(.easeInOut(duration: 0.15)) {
                        isDropdownPresented = true
                    }
                }
            } else {
                // Delay hiding dropdown slightly so button clicks on suggestion items can register
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                    if !isFieldFocused {
                        withAnimation(.easeInOut(duration: 0.12)) {
                            isDropdownPresented = false
                        }
                    }
                }
            }
        }
        .onChange(of: text) { _, _ in
            if isFieldFocused && !suggestions.isEmpty && !filteredSuggestions.isEmpty {
                isDropdownPresented = true
            }
        }
        // Autocomplete Dropdown Overlay
        .overlay(alignment: .topLeading) {
            if isDropdownPresented && !filteredSuggestions.isEmpty {
                suggestionDropdownView
                    .offset(y: (fieldSize.height > 0 ? fieldSize.height : 26) + 4)
            }
        }
        .zIndex(isDropdownPresented ? 999 : 1)
    }

    private var suggestionDropdownView: some View {
        VStack(alignment: .leading, spacing: 0) {
            ScrollView(.vertical, showsIndicators: filteredSuggestions.count > 4) {
                VStack(alignment: .leading, spacing: 2) {
                    ForEach(filteredSuggestions, id: \.self) { item in
                        Button(action: {
                            text = item
                            withAnimation(.easeInOut(duration: 0.1)) {
                                isDropdownPresented = false
                            }
                            isFieldFocused = false
                            onCommit?()
                        }) {
                            HStack(spacing: 8) {
                                if let icon = icon {
                                    Image(systemName: icon)
                                        .font(.system(size: 11))
                                        .foregroundColor(item == text ? Color.accentColor : .secondary)
                                        .frame(width: 14)
                                }

                                Text(item)
                                    .font(.system(size: 12))
                                    .foregroundColor(.primary)
                                    .lineLimit(1)

                                Spacer()

                                if item == text {
                                    Image(systemName: "checkmark")
                                        .font(.system(size: 10, weight: .bold))
                                        .foregroundColor(Color.accentColor)
                                }
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(
                                RoundedRectangle(cornerRadius: 6)
                                    .fill(
                                        hoveredItem == item
                                            ? Color.accentColor.opacity(0.12)
                                            : (item == text ? Color.secondary.opacity(0.08) : Color.clear)
                                    )
                            )
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .onHover { isHovered in
                            if isHovered {
                                hoveredItem = item
                            } else if hoveredItem == item {
                                hoveredItem = nil
                            }
                        }
                    }
                }
                .padding(4)
            }
            .frame(maxHeight: 160)
        }
        .frame(minWidth: max(fieldSize.width, 180), maxWidth: max(fieldSize.width, 280))
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(Color(nsColor: .windowBackgroundColor))
                .shadow(color: Color.black.opacity(0.16), radius: 8, x: 0, y: 4)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(Color.primary.opacity(0.12), lineWidth: 1)
        )
    }
}
