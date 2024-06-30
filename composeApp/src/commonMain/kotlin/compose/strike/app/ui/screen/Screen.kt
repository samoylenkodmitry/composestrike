package compose.strike.app.ui.screen

sealed interface Screen {
    data object Splash : Screen

    data object Main : Screen

    data object UserProfile : Screen

    data object Login : Screen
    data object Game: Screen

    data class Error(
        val message: String,
    ) : Screen
}
