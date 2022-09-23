package com.example.microneedleglucose

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
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
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.ChartData
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
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
    private var histogramBluePixel = MutableList<Int>(256) { _ -> 0 }
    private var isBlueFiltered = false
    private lateinit var lineChart: LineChart
    private val chartData = ArrayList<ChartData>()


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
                    startCroppy(savedUri!!)
//                    binding.imagePreview.setImageURI(savedUri)
//                    bitmap = binding.imagePreview.drawable.toBitmap()
//                    realUri = savedUri
//                    bitmapBlue = blueFilter(bitmap!!)
//                    histogramBluePixel = getBlueHistogram(bitmapBlue!!)
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
        binding.buttonBlueFilter.setOnClickListener(View.OnClickListener {
            if(bitmapBlue != null){
                when(isBlueFiltered){
                    true -> {
                        binding.imagePreview.setImageBitmap(bitmap)
                        isBlueFiltered = false
                    }
                    false -> {
                        binding.imagePreview.setImageBitmap(bitmapBlue)
                        isBlueFiltered = true
                    }
                }
            }
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
                        bitmapBlue = blueFilter(bitmap!!)
                        histogramBluePixel = getBlueHistogram(bitmapBlue!!)
                    }
                }
            }
        }
    }

    private fun blueFilter(inputBitmap : Bitmap) : Bitmap {
        var outputBitmap =
            createBitmap(inputBitmap.width, inputBitmap.height, Bitmap.Config.ARGB_8888)
        CoroutineScope(Dispatchers.Default).launch {
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
        CoroutineScope(Dispatchers.Default).launch {
            for (i in 0 until inputBitmap.height) {
                for (j in 0 until inputBitmap.width) {
                    tmp = 0x000000FF.toInt() and inputBitmap.getPixel(j, i).toInt()
                    histogramBlue[tmp]++
                }
            }
        }
        return histogramBlue
    }

    private fun chartInit(chart : LineChart){
        chart.setDrawGridBackground(false)
        chart.setBackgroundColor(Color.rgb(0xEF,0xEF,0xEF))
        chart.setGridBackgroundColor(Color.rgb(0xEF,0xEF,0xEF))
//
        chart.getDescription().setEnabled(false)
//        val des : Description = chart.description
//        des.setEnabled(true)
//        des.setText(String.format("Ch %d", channel))
//        des.setTextSize(15f)
//        des.setTextColor(Color.BLACK)


        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        //chart.isAutoScaleMinMaxEnabled = false

        chart.setPinchZoom(true)

        chart.xAxis.setDrawGridLines(true)
        chart.xAxis.setDrawAxisLine(true)

        val xAxis = chart.xAxis
        xAxis.isEnabled = true
        xAxis.setDrawGridLines(false)
        xAxis.position = XAxis.XAxisPosition.BOTTOM
//        xAxis.textColor = Color.BLACK
//        xAxis.textSize = 12f
//        xAxis.valueFormatter = TimeAxisValueFormat()


//        val legend: Legend = chart.legend
//        legend.isEnabled = true
//        legend.formSize = 10f
//        legend.textSize = 12f
//        legend.textColor = Color.BLACK

        val yAxis : YAxis = chart.axisLeft
        yAxis.isEnabled = true
        yAxis.textColor = Color.BLACK
        yAxis.setDrawGridLines(false)
//        yAxis.gridColor = Color.BLACK
//        yAxis.textColor = Color.BLACK
//        yAxis.textSize = 12f

        val rAxis : YAxis = chart.axisRight
        rAxis.isEnabled = false
        chart.invalidate()
    }

    fun addEntry(num : Int) {
        var data = lineChart.data
        if (data == null) {
            data = LineData()
            lineChart.data = data
        }
        var set = data.getDataSetByIndex(0)
//        var set = data.getDataSetByIndex(channel)
        if (set == null) {
            //set = createSet(channel)
            set = createSet()
            data.addDataSet(set)
        }

        data.addEntry(Entry(set.entryCount.toFloat(), num.toFloat()), 0)
        data.notifyDataChanged()

        // let the chart know it's data has changed

        lineChart.notifyDataSetChanged()

        lineChart.setVisibleXRangeMaximum(300f)
        lineChart.setVisibleXRange(0f,299f)
        // this automatically refreshes the chartArray[channel] (calls invalidate())
        lineChart.moveViewTo(data.entryCount.toFloat(), 50f, YAxis.AxisDependency.LEFT)


    }

    private fun createSet(): LineDataSet {
        val set = LineDataSet(null, "")
        set.lineWidth = 2f
        set.setDrawValues(false)
        set.valueTextColor = Color.BLACK
        set.color = Color.rgb(0xAE, 0x0B, 0xB0)

        set.mode = LineDataSet.Mode.LINEAR
        set.setDrawCircles(false)
        set.highLightColor = Color.rgb(190, 190, 190)
        return set
    }
    companion object {
        private const val RC_CROP_IMAGE = 1001
    }
}