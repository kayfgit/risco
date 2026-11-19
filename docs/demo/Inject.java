import android.os.SystemClock;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;

/** Plays a timed script of touches and keys. Lines: "<ms> down|move|up <x> <y>", "<ms> key <code>", "<ms> text <s>", "<ms> mark". */
public class Inject {
    static Object im;
    static Method inject;

    public static void main(String[] a) throws Exception {
        Class<?> c;
        try { c = Class.forName("android.hardware.input.InputManagerGlobal"); }
        catch (ClassNotFoundException e) { c = Class.forName("android.hardware.input.InputManager"); }
        im = c.getDeclaredMethod("getInstance").invoke(null);
        inject = c.getMethod("injectInputEvent", InputEvent.class, int.class);
        KeyCharacterMap kcm = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
        BufferedReader r = new BufferedReader(new FileReader(a[0]));
        long start = SystemClock.uptimeMillis();
        System.out.println("start " + start);
        long down = 0;
        String line;
        while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split(" ", 3);
            long t = start + Long.parseLong(p[0]);
            long now;
            while ((now = SystemClock.uptimeMillis()) < t) Thread.sleep(Math.min(4, t - now));
            String act = p[1];
            long n = SystemClock.uptimeMillis();
            if (act.equals("mark")) { System.out.println("mark " + (n - start)); continue; }
            if (act.equals("key")) {
                int code = Integer.parseInt(p[2]);
                send(new KeyEvent(n, n, KeyEvent.ACTION_DOWN, code, 0));
                send(new KeyEvent(n, n, KeyEvent.ACTION_UP, code, 0));
                continue;
            }
            if (act.equals("text")) {
                for (KeyEvent e : kcm.getEvents(p[2].toCharArray())) send(KeyEvent.changeTimeRepeat(e, n, 0));
                continue;
            }
            String[] xy = p[2].split(" ");
            float x = Float.parseFloat(xy[0]), y = Float.parseFloat(xy[1]);
            int action = act.equals("down") ? MotionEvent.ACTION_DOWN : act.equals("up") ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
            if (action == MotionEvent.ACTION_DOWN) down = n;
            MotionEvent e = MotionEvent.obtain(down, n, action, x, y, 1f, 1f, 0, 1f, 1f, 0, 0);
            e.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            send(e);
            e.recycle();
        }
    }

    static void send(InputEvent e) throws Exception { inject.invoke(im, e, 0); }
}
