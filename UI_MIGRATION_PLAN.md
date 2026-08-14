# CubeArray UI Migration Plan

This plan is ordered for a single implementation agent. Complete each step fully before moving to the next one.

Every step must end with:

1. Run the relevant tests.
2. Run `git diff --check`.
3. Review `git status` and the staged diff.
4. Commit only that step with a focused commit message.

Do not combine unrelated steps into one commit.

## 1. Commit Current Cleanup

Commit the current uncommitted styling cleanup in:

- `BlockIconPanel.java`
- `BlockReplacerDialog.java`
- `BlockTableDialog.java`
- `FileRenderApp.java`

Verify:

- `mvn -pl cubearray-app test`
- `git diff --check`

Suggested commit message: `finish UI styling cleanup`

## 2. Create Theme Test Infrastructure

Add test helpers for installing light and dark FlatLaf on the EDT.

Requirements:

- Isolate `Preferences` used by `ThemeManager`.
- Restore the original LAF and preferences after every test.
- Add assertions for `App.*Color` tokens in both themes.
- Ensure tests leave no windows open.

Verify:

- Theme tests pass repeatedly in isolation.
- Existing Swing tests remain independent of test order.

Suggested commit message: `add theme test infrastructure`

## 3. Finish Token Cleanup

Update `FlatLaf.properties` and `Ui.java` with missing tokens for:

- Disabled text
- Popup borders
- Dialog surfaces
- Table hover and selection
- Search highlights
- Chip backgrounds and borders
- Empty, loading, success, warning, and error states

Requirements:

- Remove unnecessary semantic color fallbacks where a token is guaranteed.
- Keep safe fallbacks for components created before FlatLaf installation.
- Ensure FlatLaf emits no custom-default parser warnings.

Verify:

- Light and dark token tests pass.
- `ThemeManager.install()` loads all expected custom keys.

Suggested commit message: `complete UI design tokens`

## 4. Complete the Ui Helper Layer

Expand `Ui.java` with reusable helpers for:

- Styled buttons
- Styled labels
- Search fields
- Scroll panes
- Padded panels
- Rounded borders and surfaces
- Multiple style classes

Requirements:

- Route all spacing through `UIScale`.
- Replace repeated literal `Insets`, layout gaps, and `EmptyBorder` calls.
- Keep `Ui.hairline(...)` theme-resolved at paint time.

Verify:

- Existing UI tests pass.
- Helpers work before and after LAF installation.

Suggested commit message: `expand shared Swing UI helpers`

## 5. Integrate Card Into the Main Window

Use `Card` for:

- Main status area
- Search/action area
- Settings groups
- Folder path controls

Requirements:

- Cards resolve surface and border colors during painting.
- Add a compact card variant for dense controls.
- Do not clip shadows inside scroll panes or dialogs.
- Add a drop-zone card only if real file-drop behavior exists.

Verify:

- Main window opens at normal and HiDPI scaling.
- Cards repaint correctly after theme switching.

Suggested commit message: `integrate card surfaces`

## 6. Redesign the Main Status Area

Replace the plain top `FlowLayout` status bar with distinct status items for:

- Files loading
- Schematic rendering
- Errors and warnings

Requirements:

- Use `Badge` or styled labels.
- Provide muted empty-state behavior.
- Update status components on the EDT.
- Preserve existing background-task behavior.

Verify:

- Status visibility changes work during loading and rendering.
- Add or update a Swing test for status updates.

Suggested commit message: `redesign main status area`

## 7. Integrate Badge and Status Styling

Replace plain semantic status text with `Badge` components.

Supported tones:

- `success`
- `warning`
- `danger`
- `brand`
- `muted`

Requirements:

- Resolve foreground and background from `UIManager` during painting.
- Do not communicate state through color alone.
- Preserve readable text and accessible tooltips.

Verify:

- Badge colors update after light/dark switching.
- Add component-level badge tests.

Suggested commit message: `add theme-aware status badges`

## 8. Improve File Table Rows

Preserve the existing `JTable` architecture and behavior.

Create dedicated theme-aware renderers for:

- Filename and path
- Schematic state
- File size and date
- Block lists
- Error states

Requirements:

- Keep primary values readable.
- Use muted secondary metadata where appropriate.
- Add badges or inline progress only where the model provides enough data.
- Preserve sorting, filtering, selection, icons, and search highlighting.
- Keep every renderer transparent so `ModernTable` owns row backgrounds.

Verify:

- Long names, missing values, failed loads, selected rows, and dark mode render correctly.
- Existing file table model tests pass.

Suggested commit message: `improve file table row renderers`

## 9. Complete ModernTable

Finish `ModernTable` behavior:

- Selection pills span the visible row width.
- Hover does not override selection.
- Keyboard selection and focus are visible.
- Empty and zero-column tables are safe.
- Headers use the token system.
- Partially clipped rows paint correctly.

Verify:

- Add tests for renderer opacity, selection background, hover state, and UI defaults.
- Manually verify multiple non-contiguous selections.

Suggested commit message: `finish modern table interactions`

## 10. Finish Search and Chip Surfaces

Complete `ChipSearchManager` and search controls:

- Rounded tokenized chip backgrounds
- Tokenized borders
- Theme-aware close buttons
- Hover and focus states
- Consistent spacing

Requirements:

- Use `Ui.searchField(...)` everywhere.
- Preserve add/remove behavior.
- Keep chips readable in dark mode.

Verify:

- Add, remove, and theme-switch tests pass.
- Search filtering behavior is unchanged.

Suggested commit message: `finish search chip styling`

## 11. Finish Popup and Context Menu Styling

Migrate every popup and context-menu surface:

- Use `Ui.scroll(...)` for popup scroll panes.
- Style popup titles as muted labels.
- Use `ghost`, `primary`, and `danger` action styles.
- Add consistent popup padding and minimum widths.
- Update table detail popups, block lists, and search suggestions.

Verify:

- Popups use no sunken default borders.
- Popup content remains correct after switching themes.

Suggested commit message: `style popups and context menus`

## 12. Finish Dialog Styling

Complete migration of:

- `BlockReplacerDialog`
- `BlockTableDialog`
- `OutputOptionsDialog`
- Chip search modal dialog

Requirements:

- Remove direct font derivations and literal spacing.
- Use consistent dialog padding and button ordering.
- Use `Ui.scroll(...)` for dialog content.
- Remove hard-coded selected/unselected renderer foreground branches.
- Preserve all AssertJ Swing component names.

Verify:

- Run every existing dialog test after each dialog migration.
- Test dialogs under both light and dark LAFs.

Suggested commit message: `finish dialog visual migration`

## 13. Redesign Settings

Replace the stacked `GridLayout` settings view with grouped cards:

- Appearance
- Application
- Visible columns
- Viewer keybindings

Requirements:

- Preserve all existing settings behavior.
- Add descriptions for theme mode and font size.
- Keep stable component names for tests.
- Keep keybinding and column controls functional.

Verify:

- Select the Settings tab in a Swing test.
- Verify theme selector, font-size selector, application actions, columns, and keybindings.

Suggested commit message: `redesign settings surface`

## 14. Complete Theme Preferences

Persist and restore:

- Theme mode
- Light theme choice
- Dark theme choice
- Font scale

Requirements:

- Keep system mode independent from explicit light/dark mode.
- Reapply font scale after every LAF installation.
- Update all existing windows on the EDT.
- Avoid resetting font scale during theme changes.

Verify:

- Persistence, clamping, and live switching tests pass.
- Theme switching works with dialogs and popups open.

Suggested commit message: `complete theme preferences`

## 15. Add Theme Variants

Decide whether to support bundled Arc, Nord, One Dark, or other IntelliJ themes.

If supported:

- Add `flatlaf-intellij-themes`.
- Expose separate light and dark theme selectors.
- Apply CubeArray custom tokens on top of each theme.
- Verify contrast for tables, dialogs, badges, and popups.

Do not add theme choices that have not been visually verified.

Suggested commit message: `add selectable FlatLaf theme variants`

## 16. Complete SVG Icon Migration

Convert remaining UI PNG icons to SVG where practical.

Keep Minecraft block icons as raster assets.

Add a centralized icon registry and cache by:

- Name
- Size
- UI color key

Provide icons for:

- Search
- Menu
- Folder
- Settings
- Tree/folder view
- Import
- Reload
- Render
- Delete
- Navigate up
- Close

Use a runtime `FlatSVGIcon.ColorFilter` and verify recoloring after theme switching.

Suggested commit message: `complete theme-aware SVG icons`

## 17. Integrate ToggleSwitch

Use `ToggleSwitch` for appropriate binary settings.

Requirements:

- Space and Enter operate it.
- Focus ring is visible.
- Animation remains EDT-safe.
- Add a state-change callback or property change event if needed.

Verify:

- Add component tests for keyboard operation, focus, and state changes.

Suggested commit message: `integrate animated settings toggles`

## 18. Improve System Theme Handling

Keep platform detection for Windows, macOS, and Linux.

Requirements:

- Avoid blocking the EDT during probes.
- Safely fall back when platform commands are unavailable.
- Decide whether system changes are checked only at startup or polled at runtime.
- Test parser behavior without requiring the host OS.

Suggested commit message: `harden system theme detection`

## 19. HiDPI and Accessibility Pass

Verify all custom painting at 100%, 150%, and 200% scaling.

Check:

- Rounded corners
- Hairline borders
- Focus indicators
- Keyboard operation
- Minimum click targets
- Large font scale
- States not conveyed by color alone

Suggested commit message: `polish UI scaling and accessibility`

## 20. Add Visual Regression Coverage

Add component-level tests for:

- `ModernTable`
- `Card`
- `Badge`
- `ToggleSwitch`
- Search chips

Add light/dark smoke tests for dialogs, popups, and the main window.

Use deterministic property and component assertions. Avoid pixel screenshots unless the environment is stable enough to support them.

Suggested commit message: `add UI visual regression coverage`

## 21. Run Complete Verification

Run:

```text
mvn -pl cubearray-app spotless:apply
mvn -pl cubearray-app test
mvn -pl cubearray-app -am -DskipTests package
mvn test
java -jar cubearray-app/target/CubeArray.jar --render <file> [out] [w] [h]
git diff --check
```

Review:

- Light theme
- Dark theme
- Theme switching with open dialogs
- Table selection and hover
- Search highlighting and chips
- Context menus and popups
- Settings tab
- HiDPI scaling
- Headless rendering

Record the existing unrelated `BlockGradientTest` failure separately if it remains.

Suggested commit message: `verify complete UI migration`

## 22. Final Commit Review

Before considering the migration complete:

- Review every migration commit.
- Confirm no unrelated files are staged.
- Confirm the working tree is clean.
- Confirm every step above has a focused commit.
- Confirm all known test failures are documented.
- Confirm the generated shaded JAR includes FlatLaf, fonts, SVG support, and theme resources.
