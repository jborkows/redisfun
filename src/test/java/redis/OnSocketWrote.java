package redis;

@FunctionalInterface
interface OnSocketWrote {
    void onWrote();
}
