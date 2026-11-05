// Reports: Daily, Weekly and Monthly charts and insights (design-spec.md §7.7).
// ulap.android.feature allows only core:common, model, domain, designsystem, ui and :shared (README §5).
plugins {
    alias(libs.plugins.ulap.android.feature)
}

android {
    namespace = "app.ulap.feature.reports"
}
