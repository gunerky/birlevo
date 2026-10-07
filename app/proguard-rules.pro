# AndroidViewModel/SavedState factories invoke these constructors reflectively.
# Keep only the constructors, allowing the implementation to be optimized.
-keepclassmembers class com.example.contentstudio.** extends androidx.lifecycle.AndroidViewModel {
    public <init>(android.app.Application);
    public <init>(android.app.Application, androidx.lifecycle.SavedStateHandle);
}

# PDFBox's optional JP2 plugin is not packaged. PDF display/raster uses Android
# PdfRenderer; page cloning preserves raw image streams without this decoder.
# JPXFilter detects the absent plugin explicitly if a future decode path calls it.
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder
