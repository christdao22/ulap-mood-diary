package app.ulap.feature.account.signin

/** Everything the sheet can ask for. One object, so the screen passes the ViewModel's methods once. */
internal class SignInActions(
    val onDismiss: () -> Unit,
    val onContinueWithGoogle: () -> Unit,
    val onShowStep: (SignInStep) -> Unit,
    val onEmailChange: (String) -> Unit,
    val onPasswordChange: (String) -> Unit,
    val onSubmit: () -> Unit,
)
