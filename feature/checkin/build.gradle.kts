// Check-in: Pick mood, Details, Response and the after-check (design-spec.md §7.2–§7.5).
// ulap.android.feature allows only core:common, model, domain, designsystem, ui and :shared (README §5).
plugins {
    alias(libs.plugins.ulap.android.feature)
}

android {
    namespace = "app.ulap.feature.checkin"
}
