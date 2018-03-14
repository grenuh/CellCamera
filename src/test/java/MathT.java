public class MathT {
    public static void main(String[] args) {
        // doIt(640, 480);
        doIt(640, 400);
        doIt(597, 312);
        //  doIt(586, 200);
    }

    static public void doIt(int getWidth, int getHeight) {
        double newWidght;
        double newHeight;
        double a1 = getWidth;
        double a2 = getHeight;
        double u = ((getWidth * 3.0) / getHeight);
        if (u > 4.0) {
            newWidght = 320;
            newHeight = new Double(a2 / (a1 / newWidght)).intValue();
        } else {
            newHeight = 240;
            newWidght = new Double(a1 / (a2 / newHeight)).intValue();
        }
        System.out.println(newWidght + " " + newHeight);
    }
}
