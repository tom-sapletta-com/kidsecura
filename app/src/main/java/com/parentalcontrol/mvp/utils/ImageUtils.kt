package com.parentalcontrol.mvp.utils

import android.graphics.Bitmap

object ImageUtils {
    
    fun cropBottomHalf(bitmap: Bitmap): Bitmap {
        val height = bitmap.height
        val width = bitmap.width
        val startY = height / 2
        val croppedHeight = height - startY
        
        return Bitmap.createBitmap(bitmap, 0, startY, width, croppedHeight)
    }
    
    fun resizeBitmap(bitmap: Bitmap, maxWidth: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxWidth) {
            return bitmap
        }
        
        val aspectRatio = height.toFloat() / width.toFloat()
        val newWidth = maxWidth
        val newHeight = (maxWidth * aspectRatio).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    fun blurFaces(bitmap: Bitmap): Bitmap {
        // TODO: Implement face detection and blurring
        // Use ML Kit Face Detection
        return bitmap
    }
}
