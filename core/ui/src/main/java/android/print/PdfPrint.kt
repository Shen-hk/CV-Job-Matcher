package android.print

import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File

/**
 * Saves a [PrintDocumentAdapter] output to a PDF file without showing the print dialog.
 *
 * Must live in the `android.print` package to access package-protected
 * [LayoutResultCallback] and [WriteResultCallback].
 */
class PdfPrint(private val attributes: PrintAttributes) {

    /**
     * @param adapter  from [android.webkit.WebView.createPrintDocumentAdapter]
     * @param output   target PDF file
     * @param onResult callback with success flag and the output file (or null on failure)
     */
    fun write(
        adapter: PrintDocumentAdapter,
        output: File,
        onResult: (Boolean, File?) -> Unit
    ) {
        adapter.onLayout(
            null,           // oldAttributes – null for first layout
            attributes,      // newAttributes
            CancellationSignal(),
            object : PrintDocumentAdapter.LayoutResultCallback() {
                override fun onLayoutFinished(info: PrintDocumentInfo, changed: Boolean) {
                    try {
                        val fd = ParcelFileDescriptor.open(
                            output,
                            ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE
                        )
                        adapter.onWrite(
                            arrayOf(PageRange.ALL_PAGES),
                            fd,
                            CancellationSignal(),
                            object : PrintDocumentAdapter.WriteResultCallback() {
                                override fun onWriteFinished(pages: Array<out PageRange>) {
                                    try { fd.close() } catch (_: Exception) {}
                                    Log.i("PdfPrint", "PDF written: ${output.absolutePath}")
                                    onResult(true, output)
                                }

                                override fun onWriteFailed(error: CharSequence?) {
                                    try { fd.close() } catch (_: Exception) {}
                                    Log.e("PdfPrint", "Write failed: $error")
                                    onResult(false, null)
                                }
                            }
                        )
                    } catch (e: Exception) {
                        Log.e("PdfPrint", "Layout/write error: ${e.message}", e)
                        onResult(false, null)
                    }
                }
            },
            null   // executor – runs on calling thread
        )
    }
}
