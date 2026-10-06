# 0.2.6 compile hotfix

Fixed the Android Settings bottom-navigation icon compilation error.

The Compose Settings icon is an extension property on `Icons.Filled` / `Icons.Outlined`, so it must be accessed through those receivers. `android.provider.Settings` is now imported as `AndroidSettings` to avoid a name collision with the Compose icon imports.
