import org.opencv.core.*;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.features2d.BFMatcher;
import org.opencv.features2d.KAZE;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.AreaAveragingScaleFilter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.sql.Array;
import java.util.ArrayList;

import static org.opencv.imgproc.Imgproc.*;

public class RetinalMatch {
    static {System.loadLibrary(Core.NATIVE_LIBRARY_NAME);}

    public static void main(String[] args) {
        String img1 = null;
        String img2 = null;

        //param check
        if (args.length != 2) {
            System.err.println("Parameters are incorrect. Correct format: RetinalMatch img1 img2");
        } else {
            img1 = args[0];
            img2 = args[1];
        }

        //Matrixes for First and Second input
        Mat FirstInput = Imgcodecs.imread(img1);
        Mat SecondInput = Imgcodecs.imread(img2);

        //resize images
        Imgproc.resize(FirstInput, FirstInput, new Size(0, 0), 0.8, 0.8, Imgproc.INTER_AREA);
        Imgproc.resize(SecondInput, SecondInput, new Size(0, 0), 0.8, 0.8, Imgproc.INTER_AREA);

        //contrast and brightness
        FirstInput.convertTo(FirstInput, -1, 1.6, -25);
        SecondInput.convertTo(SecondInput, -1, 1.6, -25);

        //split channels
        ArrayList<Mat> channels1 = new ArrayList<>(3);
        ArrayList<Mat> channels2 = new ArrayList<>(3);
        Core.split(FirstInput, channels1);
        Core.split(SecondInput,channels2);

        //apply CLAHE
        CLAHE clahe = Imgproc.createCLAHE(3, new Size(8, 8));
        clahe.apply(channels1.get(1), FirstInput);
        clahe.apply(channels2.get(1),SecondInput);

        //thresholding
        Imgproc.adaptiveThreshold(FirstInput, FirstInput, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 43, 11);
        Imgproc.adaptiveThreshold(SecondInput, SecondInput, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 43,  11);

        //apply median blur
        Imgproc.medianBlur(FirstInput, FirstInput, 13);
        Imgproc.medianBlur(SecondInput, SecondInput, 13);

        //apply erosion and dilation
        int kernelSize = 1;
        Mat element = getStructuringElement(CV_SHAPE_RECT, new Size(2 * kernelSize + 1, 2 * kernelSize + 1), new Point(kernelSize, kernelSize));
        erode(FirstInput,FirstInput,element);
        erode(SecondInput,SecondInput,element);
        dilate(FirstInput,FirstInput,element);
        dilate(SecondInput,SecondInput,element);

        //invert image
        Mat invertOne = new Mat(FirstInput.rows(),FirstInput.cols(), FirstInput.type(), new Scalar(255,255,255));
        Mat invertTwo = new Mat(SecondInput.rows(),SecondInput.cols(), SecondInput.type(), new Scalar(255,255,255));
        Core.subtract(invertOne, FirstInput, FirstInput);
        Core.subtract(invertTwo,SecondInput,SecondInput);

        //store the inverted images
        Mat FirstInvert = FirstInput;
        Mat SecondInvert = SecondInput;

        //find contours
        ArrayList<MatOfPoint> contours = new ArrayList<>();
        ArrayList<MatOfPoint> contoursTwo = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(FirstInvert, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
        Imgproc.findContours(SecondInvert, contoursTwo, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        //for storing
        Mat drawingOne = Mat.zeros(FirstInput.size(), CvType.CV_8UC3);
        Mat drawingTwo = Mat.zeros(SecondInput.size(), CvType.CV_8UC3);

        //filter by size
        for (int cIdx = 0; cIdx < contours.size(); cIdx++) {
            double contourArea = Imgproc.contourArea(contours.get(cIdx));
            Scalar color = new Scalar(255, 255, 255);
            if ( 1 < contourArea  && contourArea < 200000) {
                //System.out.println(contourArea);
                Imgproc.drawContours(drawingOne, contours, cIdx, color, 20, Imgproc.LINE_8, hierarchy, 0, new Point());
            }
        }

        //apply same for second image
        for (int cIdx = 0; cIdx < contoursTwo.size(); cIdx++) {
            double contourArea = Imgproc.contourArea(contoursTwo.get(cIdx));
            Scalar color = new Scalar(255, 255, 255);
            if ( 1 < contourArea  && contourArea < 200000) {
                //System.out.println(contourArea);
                Imgproc.drawContours(drawingTwo, contoursTwo, cIdx, color, 20, Imgproc.LINE_8, hierarchy, 0, new Point());
            }
        }

        //grayscale to make size of input args the same
        Imgproc.cvtColor(drawingOne,drawingOne, Imgproc.COLOR_BGR2GRAY);
        Imgproc.cvtColor(drawingTwo,drawingTwo, Imgproc.COLOR_BGR2GRAY);

        //store result
        Mat resultOne = new Mat(FirstInput.rows(),FirstInput.cols(),FirstInput.type());
        Mat resultTwo = new Mat(SecondInput.rows(),SecondInput.cols(),SecondInput.type());

        //bitwise and to mask drawing and inverted image, with result.
        Core.bitwise_and(drawingOne, FirstInvert,resultOne);
        Core.bitwise_and(drawingTwo, SecondInvert,resultTwo);

        double percent;
        percent = SIFT(resultOne,resultTwo);
        if (percent > 15){
            System.out.println("1");
        }
        else{
            System.out.println("0");
        }
    }

    private static double SIFT(Mat matrix1, Mat matrix2){
        KAZE detect = KAZE.create();
        BFMatcher matcher = BFMatcher.create();

        MatOfKeyPoint kp1 = new MatOfKeyPoint();
        Mat d1 = new Mat();
        Mat mask = new Mat();
        detect.detectAndCompute(matrix1,mask,kp1,d1,false);

        MatOfKeyPoint kp2 = new MatOfKeyPoint();
        Mat d2 = new Mat();
        Mat mask2 = new Mat();
        detect.detectAndCompute(matrix2,mask2,kp2,d2,false);

        ArrayList<MatOfDMatch> matchesD = new ArrayList<>();
        matcher.knnMatch(d1,d2,matchesD,2);

        double ratio = 0.75;
        ArrayList<DMatch> foundMatch = new ArrayList<>();

        for (int i=0; i<matchesD.size(); i++){
            MatOfDMatch matOfDMatch = matchesD.get(i);
            DMatch[] dArray = matOfDMatch.toArray();
            DMatch oneD = dArray[0];
            DMatch twoD = dArray[1];

            if(oneD.distance <= twoD.distance * ratio){
                foundMatch.add(oneD);
            }
        }
        int foundNum = foundMatch.size();
        double percent = (foundNum*100)/matchesD.size();
        //System.out.println(percent);
        return percent;
    }

    public static void imshow(Mat src){ //displays images
        BufferedImage bufImage;
        try {
            MatOfByte matOfByte = new MatOfByte();
            Imgcodecs.imencode(".jpg", src, matOfByte);
            byte[] byteArray = matOfByte.toArray();
            InputStream in = new ByteArrayInputStream(byteArray);
            bufImage = ImageIO.read(in);

            JFrame frame = new JFrame("Retinal Scan");
            frame.getContentPane().setLayout(new FlowLayout());
            frame.getContentPane().add(new JLabel(new ImageIcon(bufImage)));
            frame.setSize(new Dimension(400, 300));
            frame.pack();
            frame.setVisible(true);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}