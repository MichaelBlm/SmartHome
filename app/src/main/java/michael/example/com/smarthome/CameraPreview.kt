package michael.example.com.smarthome

import android.Manifest
import android.app.Activity
import android.app.ProgressDialog
import android.content.ContentValues
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageCapture
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.widget.Toast
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import android.util.Log
import android.view.Menu
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.PermissionChecker
import androidx.core.text.isDigitsOnly
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import michael.example.com.smarthome.databinding.ActivityCameraPreviewBinding
import okhttp3.*
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.log

typealias LumaListener = (luma: Double) -> Unit


class CameraPreview : AppCompatActivity() {
    private lateinit var viewBinding: ActivityCameraPreviewBinding
    private var imageCapture: ImageCapture? = null


    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    lateinit var filepath : Uri
    private var count = 1



    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityCameraPreviewBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)


        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Set up the listeners for take photo and video capture buttons
        viewBinding.uploadButton.setOnClickListener {
        startFileChooser()

        }
        viewBinding.videoCaptureButton.setOnClickListener { captureVideo()
            //Use handler to set camera capture time to 5 seconds maximum
            Handler(Looper.getMainLooper()).postDelayed({
                captureVideo()
            }, 5000)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }


    private fun startFileChooser(){
        var i = Intent()
        i.type = "video/*"
        i.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(i,"Choose a Video"),111)

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == 111 && resultCode == Activity.RESULT_OK && data != null) {
            filepath = data.data!!
            uploadFile()

        }
    }
    private fun uploadFile(){
        if(filepath!=null){
            var pd = ProgressDialog(this)
            pd.setTitle("Uploading")
            pd.show()
            var filename: String = if (intent.getStringExtra("choice")?.isDigitsOnly() == true) {
                "videos/" + "Num" + intent.getStringExtra("choice") + "_PRACTICE_" + (count++) + "Blumberg"
            } else {
                "videos/" + "H-" + intent.getStringExtra("choice") + "_PRACTICE_" + (count++) + "Blumberg"
            }
            var videoRef = FirebaseStorage.getInstance().reference.child(filename)

            videoRef.putFile(filepath)
                    .addOnSuccessListener {
                        pd.dismiss()
                        Toast.makeText(applicationContext, "File Uploaded", Toast.LENGTH_LONG).show()
                        val intent = Intent(this, GesturePreivew::class.java)
                        startActivity(intent);
                    }
                    .addOnFailureListener{p0->
                        pd.dismiss()
                        Toast.makeText(applicationContext, p0.message, Toast.LENGTH_LONG).show()

                    }
                    .addOnProgressListener { p0 ->
                        var progress = (100.0 * p0.bytesTransferred) / p0.totalByteCount
                        pd.setMessage("Uploaded ${progress.toInt()}%")

                    }
        }
    }


    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return

        viewBinding.videoCaptureButton.isEnabled = false

        val curRecording = recording
        if (curRecording != null) {
            // Stop the current recording session.
            curRecording.stop()
            viewBinding.videoCaptureButton.isEnabled = true
            recording = null
            return
        }

        // create and start a new recording session
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
            }
        }


        val mediaStoreOutputOptions = MediaStoreOutputOptions
                .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(contentValues)
                .build()

        recording = videoCapture.output
                .prepareRecording(this, mediaStoreOutputOptions)
                .apply {
                    if (PermissionChecker.checkSelfPermission(this@CameraPreview,
                                    Manifest.permission.RECORD_AUDIO) ==
                            PermissionChecker.PERMISSION_GRANTED)
                    {
                        withAudioEnabled()
                    }
                }
                .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                    when(recordEvent) {
                        is VideoRecordEvent.Start -> {
                            viewBinding.videoCaptureButton.apply {
                                text = getString(R.string.stop_capture)

                            }
                        }
                        is VideoRecordEvent.Finalize -> {
                            if (!recordEvent.hasError()) {
                                val msg = "Video capture succeeded: " +
                                        "${recordEvent.outputResults.outputUri}"
                                Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT)
                                        .show()
                                Log.d(TAG, msg)

                            } else {
                                recording?.close()
                                recording = null
                                Log.e(TAG, "Video capture ends with error: " +
                                        "${recordEvent.error}")
                            }
                            viewBinding.videoCaptureButton.apply {
                                text = getString(R.string.start_capture)
                                isEnabled = true
                            }
                        }
                    }
                }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                    }

            val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.LOWEST))
                    .build()
            videoCapture = VideoCapture.withOutput(recorder)


            // Select Front Camera as a default
            val cameraSelector: CameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider
                        .bindToLifecycle(this, cameraSelector, preview, videoCapture)
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
                baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
                mutableListOf (
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO
                ).apply {
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                        add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                }.toTypedArray()
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults:
            IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}