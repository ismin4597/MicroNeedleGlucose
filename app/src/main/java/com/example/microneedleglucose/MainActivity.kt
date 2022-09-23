package com.example.microneedleglucose

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color.blue
import android.graphics.drawable.BitmapDrawable
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.set
import androidx.core.net.toUri
import com.example.microneedleglucose.databinding.ActivityMainBinding
import com.lyrebirdstudio.croppylib.Croppy
import com.lyrebirdstudio.croppylib.main.CropRequest
import com.lyrebirdstudio.croppylib.main.CroppyActivity
import com.lyrebirdstudio.croppylib.main.CroppyTheme
import com.lyrebirdstudio.croppylib.main.StorageType
import com.lyrebirdstudio.croppylib.util.file.FileCreator
import com.lyrebirdstudio.croppylib.util.file.FileExtension
import com.lyrebirdstudio.croppylib.util.file.FileOperationRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File


val PERMISSION_CAMERA = 1000
val REQUEST_CAMERA = 2000
class MainActivity : AppCompatActivity() {
    private var mBinding : ActivityMainBinding? = null
    private val binding get() = mBinding!!
    private var realUri : Uri? = null
    private var cropUri : Uri? = null
    private var drawable : BitmapDrawable? = null
    private var bitmap : Bitmap? = null
    private var bitmapBlue : Bitmap? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val requestForActivityResult : ActivityResultLauncher<Intent> = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ){activityResult ->
            when(activityResult.resultCode){
                RESULT_OK -> {
                    val savedUri = activityResult.data?.getParcelableExtra<Uri>("savedUri")
                    binding.imagePreview.setImageURI(savedUri)
                    bitmap = binding.imagePreview.drawable.toBitmap()
                    realUri = savedUri

                }
            }
        }
        val getContent : ActivityResultLauncher<String> = registerForActivityResult(
//        ActivityResultContracts.StartActivityForResult()
            ActivityResultContracts.GetContent()
        ) {uri ->
            binding.imagePreview.setImageURI(uri)
            bitmap = binding.imagePreview.drawable.toBitmap()
        }
        requirePermissions(arrayOf(android.Manifest.permission.CAMERA), PERMISSION_CAMERA)
        binding.buttonCamera.setOnClickListener(View.OnClickListener {
            val cameraActivity = Intent(this@MainActivity, cameraActivity::class.java)
            requestForActivityResult.launch(cameraActivity)
//            openCamera()
        })
        binding.buttonLoad.setOnClickListener(View.OnClickListener {
            getContent.launch("image/*")
        })

        binding.indicatorSeekbar.isClickable = false


    }

    private fun startCroppy(uri: Uri) {
        val externalCropRequest = CropRequest.Auto(
            sourceUri = uri,
            requestCode = RC_CROP_IMAGE,
            storageType = StorageType.CACHE
        )

        val croppedImageFile : File = FileCreator.createFile(
            FileOperationRequest(StorageType.EXTERNAL, newFileName(), FileExtension.PNG),
            application.applicationContext)
        val destinationUri = croppedImageFile.toUri()

        cropUri = destinationUri

        val manualCropRequest = CropRequest.Manual(
            sourceUri =  uri,
            destinationUri = destinationUri!!,
            requestCode = RC_CROP_IMAGE
        )

        val themeCropRequest = CropRequest.Manual(
            sourceUri = uri,
            destinationUri = destinationUri!!,
            requestCode = RC_CROP_IMAGE,
            croppyTheme = CroppyTheme(com.lyrebirdstudio.croppylib.R.color.blue)
        )

        Croppy.start(this, manualCropRequest)
    }

    fun requirePermissions(permissions : Array<String>, requestCode : Int){
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            permissionGranted(requestCode)
        } else {
            val isAllPermissionsGranted = permissions.all{checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED}
            if (isAllPermissionsGranted) {
                permissionGranted(requestCode)
            } else {
                ActivityCompat.requestPermissions(this, permissions, requestCode)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if(grantResults.all {it == PackageManager.PERMISSION_GRANTED}) {
            permissionGranted(requestCode)
        }else{
            permissionDenied(requestCode)
        }
    }

    private fun permissionGranted(requestCode : Int) {
        when (requestCode){
//            PERMISSION_CAMERA -> openCamera()
        }
    }

    private fun permissionDenied(requestCode: Int) {
        when (requestCode){
            PERMISSION_CAMERA -> Toast.makeText(
                this,
                "Camera permission is required to use camera.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun openCamera() {
        val intent  = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        createImageUri(newFileName(), "image/png")?.let {uri ->
            realUri = uri
            intent.putExtra(MediaStore.EXTRA_OUTPUT, realUri)
            startActivityForResult(intent, REQUEST_CAMERA)
        }
    }

    @SuppressLint("SimpleDateFormat")
    private fun newFileName() : String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss")
        val filename = sdf.format(System.currentTimeMillis())
        return "$filename.png"
    }

    private fun createImageUri(filename : String, mimeType : String): Uri?{
        var values = ContentValues()
        values.put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        values.put(MediaStore.Images.Media.MIME_TYPE, mimeType)
        return this.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    private fun createFileUri(filename: String, mimeType: String): Uri? {
        var values = ContentValues()
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        return contentResolver.insert(MediaStore.Files.getContentUri("external"), values);
    }

    override fun onActivityResult(requestCode : Int, resultCode : Int, data : Intent?){
        super.onActivityResult(requestCode, resultCode, data)

        if(resultCode == RESULT_OK){
            when(requestCode){
                REQUEST_CAMERA->{
                    realUri?.let{ uri ->
                        binding.imagePreview.setImageURI(uri)
                        bitmap = binding.imagePreview.drawable.toBitmap()
                        startCroppy(realUri!!)
                        Log.d("bitmapTest", "Width : ${bitmap!!.width.toString()} \tHeight : ${bitmap!!.height.toString()}")
                        Log.d("bitmapTest", "Pixel 1 : ${String.format("%x", bitmap!!.getPixel(0,0))}")

                    }
                }
                RC_CROP_IMAGE->{
                    cropUri?.let{ uri ->
                        binding.imagePreview.setImageURI(uri)
                        bitmap = binding.imagePreview.drawable.toBitmap()
                        Log.d("bitmapTest", "Width : ${bitmap!!.width.toString()} \tHeight : ${bitmap!!.height.toString()}")
                        Log.d("bitmapTest", "Pixel 1 : ${String.format("%x", bitmap!!.getPixel(0,0))}")
                        binding.imagePreview.setImageBitmap(blueFilter(bitmap!!))
                        val histogram = getBlueHistogram(bitmap!!)
                        Log.d("bitmapTest", "Histogram : " + histogram.toString())
                    }
                }
            }
        }
    }

    private fun blueFilter(inputBitmap : Bitmap) : Bitmap {
        CoroutineScope(Dispatchers.Default).launch {
            var outputBitmap =
                createBitmap(inputBitmap.width, inputBitmap.height, Bitmap.Config.ARGB_8888)
            for (i in 0 until inputBitmap.height) {
                for (j in 0 until inputBitmap.width) {
                    outputBitmap[j, i] = 0xFF0000FF.toInt() and inputBitmap.getPixel(j, i).toInt()
                }
            }
        }
        return outputBitmap
    }

    private fun getBlueHistogram(inputBitmap: Bitmap) :MutableList<Int> {
        val histogramBlue = MutableList<Int>(256) { _ -> 0 }
        var tmp : Int = 0
        for(i in 0 until inputBitmap.height) {
            for(j in 0 until inputBitmap.width) {
                tmp = 0x000000FF.toInt() and inputBitmap.getPixel(j,i).toInt()
                histogramBlue[tmp]++
            }
        }
        return histogramBlue
    }


    companion object {
        private const val RC_CROP_IMAGE = 1001
    }
}