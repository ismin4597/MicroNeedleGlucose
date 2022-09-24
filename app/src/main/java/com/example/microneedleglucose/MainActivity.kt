package com.example.microneedleglucose

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.set
import androidx.core.net.toUri
import com.example.microneedleglucose.databinding.ActivityMainBinding
import com.github.mikephil.charting.charts.Chart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.lyrebirdstudio.croppylib.Croppy
import com.lyrebirdstudio.croppylib.main.CropRequest
import com.lyrebirdstudio.croppylib.main.CroppyTheme
import com.lyrebirdstudio.croppylib.main.StorageType
import com.lyrebirdstudio.croppylib.util.file.FileCreator
import com.lyrebirdstudio.croppylib.util.file.FileExtension
import com.lyrebirdstudio.croppylib.util.file.FileOperationRequest
import kotlinx.coroutines.*
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
    private var bitmapGreen : Bitmap? = null
    private var histogramBluePixel = MutableList<Int>(256) { _ -> 0 }
    private var histogramGreenPixel = MutableList<Int>(256) { _ -> 0 }
    private var isBlueFiltered = false
    lateinit var histogramChart : LineChart
    private var isUpdated = false



//    private lateinit var lineChart: LineChart


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val mHandler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage (msg : Message) {
                clearChart(histogramChart)
                drawHistogram(histogramChart, HISTOGRAM_BLUE_CHANNEL, histogramBluePixel)
                drawHistogram(histogramChart, HISTOGRAM_GREEN_CHANNEL, histogramGreenPixel)
            }
        }
        val requestForActivityResult : ActivityResultLauncher<Intent> = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ){activityResult ->
            when(activityResult.resultCode){
                RESULT_OK -> {
                    val savedUri = activityResult.data?.getParcelableExtra<Uri>("savedUri")
                    startCroppy(savedUri!!)
                }
            }
        }
        val getContent : ActivityResultLauncher<String> = registerForActivityResult(
//        ActivityResultContracts.StartActivityForResult()
            ActivityResultContracts.GetContent()
        ) {uri ->
            startCroppy(uri)
//            binding.imagePreview.setImageURI(uri)
//            bitmap = binding.imagePreview.drawable.toBitmap()
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

        binding.imagePreview.setOnClickListener(View.OnClickListener {
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

        binding.buttonUpdate.setOnClickListener(View.OnClickListener {
            val msg : Message = Message()

            CoroutineScope(Dispatchers.Default).launch {
                val update = async(Dispatchers.Default){
                    if(bitmap != null)
                        updateHistogram(bitmap!!)
                }
                update.await()
                mHandler.sendMessage(msg)
            }

//            CoroutineScope(Dispatchers.Default).launch{
//                updateHistogram(bitmap!!)
//            }
//            clearChart(histogramChart)
//
        })
        histogramChart = binding.histogramChart
        chartInit(histogramChart)
        histogramChart.setNoDataText("")
        histogramChart.setBackgroundColor(Color.rgb(0xE3,0xE3,0xE3))
        histogramChart.setDrawGridBackground(false)

        // from: https://github.com/PhilJay/MPAndroidChart/issues/89

        // from: https://github.com/PhilJay/MPAndroidChart/issues/89

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
//                        bitmapBlue = blueFilter(bitmap!!)
                        bitmapBlue = blueFilter(bitmap!!)
                        bitmapGreen = greenFilter(bitmap!!)
//                        updateHistogram(bitmap!!)
//                        clearChart(histogramChart)
//                        drawHistogram(histogramChart, HISTOGRAM_BLUE_CHANNEL, histogramBluePixel)
//                        drawHistogram(histogramChart, HISTOGRAM_GREEN_CHANNEL, histogramGreenPixel)
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

    private fun greenFilter(inputBitmap: Bitmap) : Bitmap {
        var outputBitmap =
            createBitmap(inputBitmap.width, inputBitmap.height, Bitmap.Config.ARGB_8888)
        CoroutineScope(Dispatchers.Default).launch {
            for (i in 0 until inputBitmap.height) {
                for (j in 0 until inputBitmap.width) {
                    val pixel = inputBitmap.getPixel(j,i).toInt()
                    val green = pixel and 0xFF00FF00.toInt()
                    outputBitmap[j, i] = green
                }
            }
        }
        return outputBitmap
    }

    private fun updateHistogram(inputBitmap: Bitmap) {
        var tmp1 : Int = 0
        var tmp2 : Int = 0
        var outputBitmapBlue = MutableList(256){_->0}
        var outputBitmapGreen = MutableList(256){_->0}

        for (i in 0 until inputBitmap.height) {
            for (j in 0 until inputBitmap.width) {
                val pixel = inputBitmap.getPixel(j, i).toInt()
                tmp1 = (0x000000FF.toInt() and pixel)
                tmp2 = (0x0000FF00.toInt() and pixel) shr 8
                outputBitmapBlue[tmp1]++
                outputBitmapGreen[tmp2]++
            }
        }
        histogramBluePixel = outputBitmapBlue
        histogramGreenPixel = outputBitmapGreen
//        return listOf(outputBitmapGreen, outputBitmapBlue)
    }
    private fun clearChart(chart : LineChart) {
        chart.fitScreen()
        chart.data?.clearValues()
        chart.notifyDataSetChanged()
        chart.clear()
        chart.invalidate()
    }
    private fun drawHistogram(chart : LineChart, channel: Int, inputList : MutableList<Int>){
        for(element in inputList){
            addEntry(chart, channel, element)
        }
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

    fun addEntry(chart : LineChart, channel: Int, num : Int) {
        var data = chart.data
        if (data == null) {
            data = LineData()
            chart.data = data
        }
        var set = data.getDataSetByIndex(channel)
//        var set = data.getDataSetByIndex(channel)
        if (set == null) {
            //set = createSet(channel)
            set = createSet(channel)
            data.addDataSet(set)
        }

        data.addEntry(Entry(set.entryCount.toFloat(), num.toFloat()), channel)
        data.notifyDataChanged()

        // let the chart know it's data has changed

        chart.notifyDataSetChanged()
        chart.setVisibleXRangeMaximum(256f)
        chart.setVisibleXRange(0f,255f)
        // this automatically refreshes the chartArray[channel] (calls invalidate())
        chart.moveViewTo(data.entryCount.toFloat(), 50f, YAxis.AxisDependency.LEFT)


    }

    private fun createSet(channel : Int): LineDataSet {
        val set = LineDataSet(null, "CH${channel}")
        set.lineWidth = 2f
        set.setDrawValues(false)
        set.valueTextColor = Color.BLACK
        if(channel == HISTOGRAM_BLUE_CHANNEL){
            set.color = Color.rgb(0x00,0x00,0xEE)
        } else {
            set.color = Color.rgb(0x00,0xEE,0x00)
        }
//        when(channel) {
//            HISTOGRAM_BLUE_CHANNEL -> set.color = Color.rgb(0x00,0x00,0xEE)
//            HISTOGRAM_GREEN_CHANNEL -> set.color = Color.rgb(0x00, 0xEE, 0x00)
//        }

        set.mode = LineDataSet.Mode.LINEAR
        set.setDrawCircles(false)
        set.highLightColor = Color.rgb(190, 190, 190)
        return set
    }
    companion object {
        private const val RC_CROP_IMAGE = 1001
        private const val HISTOGRAM_BLUE_CHANNEL = 0
        private const val HISTOGRAM_GREEN_CHANNEL = 1
    }
}