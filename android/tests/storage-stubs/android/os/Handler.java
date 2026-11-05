package android.os;public final class Handler{public Handler(Looper ignored){}public boolean post(Runnable work){work.run();return true;}}
