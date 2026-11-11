package com.stellapp.disc.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Black-on-white QR code (scanners need the contrast), drawn straight from ZXing's bit matrix. */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        try {
            QRCodeWriter().encode(
                text,
                BarcodeFormat.QR_CODE,
                0,
                0,
                mapOf(
                    EncodeHintType.MARGIN to 1,
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                    EncodeHintType.CHARACTER_SET to "UTF-8",
                ),
            )
        } catch (e: Exception) {
            null
        }
    }
    Canvas(
        modifier
            .background(Color.White)
            .padding(8.dp)
    ) {
        val m = matrix ?: return@Canvas
        val n = m.width
        val cell = size.minDimension / n
        val offsetX = (size.width - cell * n) / 2f
        val offsetY = (size.height - cell * n) / 2f
        for (y in 0 until n) {
            for (x in 0 until n) {
                if (m.get(x, y)) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(offsetX + x * cell, offsetY + y * cell),
                        size = Size(cell + 0.6f, cell + 0.6f),
                    )
                }
            }
        }
    }
}
