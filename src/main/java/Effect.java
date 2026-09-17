sealed interface Effect {
}

record PrintText(String text) implements Effect {
}
