/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zxing;

import com.google.zxing.ResultPoint;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.NinePatch;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import ooo.zuo.zxingdemo.R;
import zxing.camera.CameraManager;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder rectangle and partial
 * transparency outside it, as well as the laser scanner animation and result points.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class ViewfinderView extends View {

    private static final int[] SCANNER_ALPHA = {0, 64, 128, 192, 255, 192, 128, 64};
    private static final long ANIMATION_DELAY = 80L;
    private static final int CURRENT_POINT_OPACITY = 0xA0;
    private static final int MAX_RESULT_POINTS = 20;
    private static final int POINT_SIZE = 6;

    private CameraManager cameraManager;
    private final Paint paint;
    private Bitmap resultBitmap;
    private final int maskColor;
    private final int resultColor;
    private final int laserColor;
    private final int resultPointColor;
    private int scannerAlpha;
    private List<ResultPoint> possibleResultPoints;
    private List<ResultPoint> lastPossibleResultPoints;
    private float mDensity;
    private int mDriverWidthPixels;
    private NinePatch mScanner_rect;
    private NinePatch mScanner_line;
    private Rect mFrame;
    private Rect mScanner;
    private int mDx = 0;
    private String tipsText = "请扫描包装盒上的商品条形码";
    private Paint textPaint = new Paint();
    private float textWidth;
    private float textHight;
    private Rect textRect = new Rect();

    // This constructor is used when the class is built from an XML resource.
    public ViewfinderView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Initialize these once for performance rather than calling them every time in onDraw().
        mDensity = getResources().getDisplayMetrics().density;
        mDriverWidthPixels = getResources().getDisplayMetrics().widthPixels;
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setAntiAlias(true);
        textPaint.setTextSize(30);
        textPaint.setColor(Color.WHITE);
        textWidth = textPaint.measureText(tipsText);
        Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
        textHight = fontMetrics.bottom - fontMetrics.top;
        Resources resources = getResources();
        maskColor = resources.getColor(R.color.viewfinder_mask);
        resultColor = resources.getColor(R.color.result_view);
        laserColor = resources.getColor(R.color.viewfinder_laser);
        resultPointColor = resources.getColor(R.color.possible_result_points);
        scannerAlpha = 0;
        possibleResultPoints = new ArrayList<>(5);
        lastPossibleResultPoints = null;
        Bitmap rectBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.v320_icon_barcode_sacnner_rect);
        mScanner_rect = new NinePatch(rectBitmap,rectBitmap.getNinePatchChunk(),null);
        Bitmap lineBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.v320_icon_sanner_line);
        mScanner_line = new NinePatch(lineBitmap,lineBitmap.getNinePatchChunk(),null);

//        int ret_top = (int) (94 * mDensity);
//        int ret_left = (int) (50 * mDensity);
//        int ret_right = (int) (mDriverWidthPixels - 50 * mDensity);
//        int ret_bottom = ret_top + (ret_right - ret_left);
//        mFrame = new Rect(ret_left,ret_top,ret_right,ret_bottom);


    }

    public void setCameraManager(CameraManager cameraManager) {
        this.cameraManager = cameraManager;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int minSize = (int) (260 * mDensity);
        if (widthMode == MeasureSpec.EXACTLY) {
            if (widthSize < 260 * mDensity) {
                widthSize = minSize;
            }
        }
        if (heightMode == MeasureSpec.EXACTLY) {
            if (heightSize < 260 * mDensity) {
                heightSize = minSize;
            }
        }

        widthMeasureSpec = MeasureSpec.makeMeasureSpec(widthSize,widthMode);
        heightMeasureSpec = MeasureSpec.makeMeasureSpec(heightSize,heightMode);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @SuppressLint("DrawAllocation")
    @Override
    public void onDraw(Canvas canvas) {
        if (cameraManager == null) {
            return; // not ready yet, early draw before done configuring
        }

        mFrame = cameraManager.getFramingRect();
        Rect previewFrame = cameraManager.getFramingRectInPreview();
        if (mFrame == null || previewFrame == null) {
            return;
        }
        if (mScanner==null){
            mScanner = new Rect(mFrame.left,mFrame.top,mFrame.right,mFrame.top+25);
            textRect.left = (int) (mFrame.left + (mFrame.width() - textWidth) / 2);
            textRect.top = mFrame.bottom+30;
            textRect.right = (int) (textRect.left+textWidth);
            textRect.bottom = (int) (textRect.top+textHight);
        }
        int width = canvas.getWidth();
        int height = canvas.getHeight();

        // Draw the exterior (i.e. outside the framing rect) darkened
        paint.setColor(resultBitmap != null ? resultColor : maskColor);
        canvas.drawRect(0, 0, width, mFrame.top, paint);
        canvas.drawRect(0, mFrame.top, mFrame.left, mFrame.bottom + 1, paint);
        canvas.drawRect(mFrame.right + 1, mFrame.top, width, mFrame.bottom + 1, paint);
        canvas.drawRect(0, mFrame.bottom + 1, width, height, paint);
        mScanner_rect.draw(canvas, mFrame);

        canvas.drawText(tipsText,textRect.left,textRect.bottom,textPaint);

        if (resultBitmap != null) {
            // Draw the opaque result bitmap over the scanning rectangle
            paint.setAlpha(CURRENT_POINT_OPACITY);
            canvas.drawBitmap(resultBitmap, null, mFrame, paint);
        } else {
//            25像素高
            mScanner.offset(0,5);
            mScanner_line.draw(canvas,mScanner);
            if (mScanner.bottom>=mFrame.bottom){
                mScanner.offset(0,mFrame.top-mScanner.top);
            }
            invalidate();

//            // Draw a red "laser scanner" line through the middle to show decoding is active
//            paint.setColor(laserColor);
//            paint.setAlpha(SCANNER_ALPHA[scannerAlpha]);
//            scannerAlpha = (scannerAlpha + 1) % SCANNER_ALPHA.length;
//            int middle = frame.height() / 2 + frame.top;
//            canvas.drawRect(frame.left + 2, middle - 1, frame.right - 1, middle + 2, paint);
//
//            float scaleX = frame.width() / (float) previewFrame.width();
//            float scaleY = frame.height() / (float) previewFrame.height();
//
//            List<ResultPoint> currentPossible = possibleResultPoints;
//            List<ResultPoint> currentLast = lastPossibleResultPoints;
//            int frameLeft = frame.left;
//            int frameTop = frame.top;
//            if (currentPossible.isEmpty()) {
//                lastPossibleResultPoints = null;
//            } else {
//                possibleResultPoints = new ArrayList<>(5);
//                lastPossibleResultPoints = currentPossible;
//                paint.setAlpha(CURRENT_POINT_OPACITY);
//                paint.setColor(resultPointColor);
//                synchronized (currentPossible) {
//                    for (ResultPoint point : currentPossible) {
//                        canvas.drawCircle(frameLeft + (int) (point.getX() * scaleX),
//                                frameTop + (int) (point.getY() * scaleY),
//                                POINT_SIZE, paint);
//                    }
//                }
//            }
//            if (currentLast != null) {
//                paint.setAlpha(CURRENT_POINT_OPACITY / 2);
//                paint.setColor(resultPointColor);
//                synchronized (currentLast) {
//                    float radius = POINT_SIZE / 2.0f;
//                    for (ResultPoint point : currentLast) {
//                        canvas.drawCircle(frameLeft + (int) (point.getX() * scaleX),
//                                frameTop + (int) (point.getY() * scaleY),
//                                radius, paint);
//                    }
//                }
//            }

//            // Request another update at the animation interval, but only repaint the laser line,
//            // not the entire viewfinder mask.
//            postInvalidateDelayed(ANIMATION_DELAY,
//                    frame.left - POINT_SIZE,
//                    frame.top - POINT_SIZE,
//                    frame.right + POINT_SIZE,
//                    frame.bottom + POINT_SIZE);
        }
    }

//    public void drawViewfinder() {
//        Bitmap resultBitmap = this.resultBitmap;
//        this.resultBitmap = null;
//        if (resultBitmap != null) {
//            resultBitmap.recycle();
//        }
//        invalidate();
//    }

//    /**
//     * Draw a bitmap with the result points highlighted instead of the live scanning display.
//     *
//     * @param barcode An image of the decoded barcode.
//     */
//    public void drawResultBitmap(Bitmap barcode) {
//        resultBitmap = barcode;
//        invalidate();
//    }

    public void addPossibleResultPoint(ResultPoint point) {
        List<ResultPoint> points = possibleResultPoints;
        synchronized (points) {
            points.add(point);
            int size = points.size();
            if (size > MAX_RESULT_POINTS) {
                // trim it
                points.subList(0, size - MAX_RESULT_POINTS / 2).clear();
            }
        }
    }

}
