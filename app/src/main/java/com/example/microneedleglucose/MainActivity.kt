package com.example.microneedleglucose

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.set
import androidx.core.net.toUri
import com.example.microneedleglucose.databinding.ActivityMainBinding
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.lyrebirdstudio.croppylib.Croppy
import com.lyrebirdstudio.croppylib.main.CropRequest
import com.lyrebirdstudio.croppylib.main.StorageType
import com.lyrebirdstudio.croppylib.util.file.FileCreator
import com.lyrebirdstudio.croppylib.util.file.FileExtension
import com.lyrebirdstudio.croppylib.util.file.FileOperationRequest
import kotlinx.coroutines.*
import java.io.File
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity() {
    private var mBinding : ActivityMainBinding? = null
    private val binding get() = mBinding!!
    private var realUri : Uri? = null
    private var cropUri : Uri? = null
    private var drawable : BitmapDrawable? = null
    private var bitmap : Bitmap? = null
//    private var bitmapBlue : Bitmap? = null
//    private var bitmapGreen : Bitmap? = null
    private var bitmapCustom : Bitmap? = null
    private var bitmapCustomRev : Bitmap? = null
    private var isFiltered : Boolean = false
    private var isClicked : Boolean = false
    private var histogramBluePixel = MutableList<Int>(256) { _ -> 0 }
    private var histogramGreenPixel = MutableList<Int>(256) { _ -> 0 }

    private var currentDataCnt = 0
    private var currentGlucose : Double? = null

//    private var bluePixelMeanFiltered : Double = 0.0
//    private var bluePixelMeanTotal : Double = 0.0
    private var greenPixelMeanFiltered : Double = 0.0
    private var greenPixelMeanTotal : Double = 0.0
    private var filteredArea : Double = 0.0

//    lateinit var histogramChart : LineChart
    lateinit var glucoseChart : LineChart
    private var glucoseMeanArray:ArrayList<Double> = ArrayList<Double>()
    private var glucoseMeanCntArray:ArrayList<Int> = ArrayList<Int>()
    

//    private lateinit var lineChart: LineChart


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
            val cameraActivity = Intent(this@MainActivity, CameraActivity::class.java)
            requestForActivityResult.launch(cameraActivity)
//            openCamera()
        })
        binding.buttonLoad.setOnClickListener(View.OnClickListener {
            getContent.launch("image/*")
        })

        binding.imagePreview.clipToOutline = true
        binding.imagePreview.setOnClickListener(View.OnClickListener {
            if(bitmapCustom != null){
                when(isClicked){
                    false-> {
                        binding.imagePreview.setImageBitmap(bitmapCustom)
                        isClicked = true
                    }
                    true-> {
                        binding.imagePreview.setImageBitmap(bitmap)
                        isClicked = false
                    }
                }
            }
        })

        glucoseChart = binding.glucoseChart
        chartInit(glucoseChart)
        glucoseChart.setNoDataText("")
        glucoseChart.setBackgroundColor(Color.rgb(0xE3,0xE3,0xE3))
        glucoseChart.setDrawGridBackground(false)
        glucoseChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener{
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                val xAxisLabel = e?.x.let{
                    glucoseChart.xAxis.valueFormatter.getAxisLabel(it!!, glucoseChart.xAxis)
                }
                val i = glucoseChart.data.dataSets[0].getEntryIndex(e)
                Log.d("ChartRemove", "Removed index : $i\tRemoved Entry : $e")
                if(i == -1)
                    return
                removeEntryGlucose(glucoseChart, i)
            }

            override fun onNothingSelected() {
            }

        })
        binding.meanText.visibility = INVISIBLE
        binding.buttonCalibrate.setOnClickListener {
            if (currentGlucose != null && glucoseMeanArray.size != 0) {
                val newAvg = (glucoseMeanArray.last() * glucoseMeanCntArray.last() + currentGlucose!!) / (glucoseMeanCntArray.last()+1)// 나눗셈안했음
                glucoseMeanArray[glucoseMeanArray.size-1] = newAvg
                glucoseMeanCntArray[glucoseMeanCntArray.size-1] += 1
                clearChart(glucoseChart)
                for (element in glucoseMeanArray){
                    addEntryGlucose(glucoseChart, GLUCOSE_FILTERED_CHANNEL, element)
                }
                currentGlucose = null
                isFiltered = false
            }
        }
        binding.buttonAdd.setOnClickListener {
            if(currentGlucose != null) {
                glucoseMeanArray.add(currentGlucose!!)
                glucoseMeanCntArray.add(1)
                addEntryGlucose(glucoseChart, GLUCOSE_FILTERED_CHANNEL, glucoseMeanArray.last())
                currentGlucose = null
                isFiltered = false
            }
        }

        binding.indicatorSeekbar.isEnabled = false
        val input = openFileInput("glucoseTest.txt")
        val str = input.reader().readText()
        val strArray = str.split("\n")
        for (element in strArray){
            val y = element.toDouble()
            glucoseMeanArray.add(y)
            glucoseMeanCntArray.add(1)
            addEntryGlucose(glucoseChart, GLUCOSE_FILTERED_CHANNEL, y)
        }

    }

    private fun startCroppy(uri: Uri) {
//        val externalCropRequest = CropRequest.Auto(
//            sourceUri = uri,
//            requestCode = RC_CROP_IMAGE,
//            storageType = StorageType.CACHE
//        )

        val croppedImageFile : File = FileCreator.createFile(
            FileOperationRequest(StorageType.EXTERNAL, newFileName(), FileExtension.PNG),
            application.applicationContext)
        val destinationUri = croppedImageFile.toUri()

        cropUri = destinationUri

        val manualCropRequest = CropRequest.Manual(
            sourceUri =  uri,
            destinationUri = destinationUri,
            requestCode = RC_CROP_IMAGE
        )

//        val themeCropRequest = CropRequest.Manual(
//            sourceUri = uri,
//            destinationUri = destinationUri!!,
//            requestCode = RC_CROP_IMAGE,
//            croppyTheme = CroppyTheme(com.lyrebirdstudio.croppylib.R.color.blue)
//        )

        Croppy.start(this, manualCropRequest)
    }

    private fun requirePermissions(permissions : Array<String>, requestCode : Int){
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

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onActivityResult(requestCode : Int, resultCode : Int, data : Intent?){
        super.onActivityResult(requestCode, resultCode, data)

        if(resultCode == RESULT_OK){
            when(requestCode){
                REQUEST_CAMERA->{
                    realUri?.let{ uri ->
                        binding.imagePreview.setImageURI(uri)
                        bitmap = binding.imagePreview.drawable.toBitmap()
                        startCroppy(realUri!!)
                    }
                }
                RC_CROP_IMAGE->{
                    cropUri?.let{ uri ->
                        val msg : Message = Message()
                        binding.imagePreview.setImageURI(uri)
                        bitmap = binding.imagePreview.drawable.toBitmap()

                        CoroutineScope(Dispatchers.Default).launch {
                            val filter = async(Dispatchers.Default){
//                                bitmapBlue = blueFilter(bitmap!!)
//                                bitmapGreen = greenFilter(bitmap!!)
                                val filterResult = customFilter(bitmap!!)
                                bitmapCustom = filterResult[0]
                                bitmapCustomRev = filterResult[1]
                                isFiltered = true
                            }

                            filter.await()

                            var glucoseResult = 0.0
                            val update = async(Dispatchers.Default){
                                if(bitmapCustom != null)
                                {
                                    glucoseResult = getFilteredBlueMean(bitmapCustom!!) - getFilteredBlueMean(bitmapCustomRev!!)
                                }
                            }
                            update.await()
                            withContext(Dispatchers.Main){
                                currentGlucose = glucoseResult
                                if(glucoseResult != 0.0){
                                    Log.d("GlucoseResult", glucoseResult.toString())
                                    val sdf = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())
                                    val timeStamp = sdf.format(System.currentTimeMillis())
                                    binding.meanText.text = timeStamp
                                    binding.meanText.visibility = VISIBLE
                                    if(glucoseResult >= GLUCOSE_HIGH_THRESHOLD){
                                        binding.indicatorSeekbar.progress = GLUCOSE_HIGH_SEEKBAR
                                        binding.glucoseTextView.text = GLUCOSE_HIGH_STRING
                                    }else if(glucoseResult >= GLUCOSE_LOW_THRESHOLD){
                                        binding.indicatorSeekbar.progress = GLUCOSE_MODERATE_SEEKBAR
                                        binding.glucoseTextView.text = GLUCOSE_MODERATE_STRING
                                    }else{
                                        binding.indicatorSeekbar.progress = GLUCOSE_LOW_SEEKBAR
                                        binding.glucoseTextView.text = GLUCOSE_LOW_STRING
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun blueFilter(inputBitmap : Bitmap) : Bitmap {
        val outputBitmap =
            createBitmap(inputBitmap.width, inputBitmap.height, Bitmap.Config.ARGB_8888)
        for (i in 0 until inputBitmap.height) {
            for (j in 0 until inputBitmap.width) {
                outputBitmap[j, i] = 0xFF0000FF.toInt() and inputBitmap.getPixel(j, i).toInt()
            }
        }
        return outputBitmap
    }

    private fun greenFilter(inputBitmap: Bitmap) : Bitmap {
        val outputBitmap =
            createBitmap(inputBitmap.width, inputBitmap.height, Bitmap.Config.ARGB_8888)
        for (i in 0 until inputBitmap.height) {
            for (j in 0 until inputBitmap.width) {
                val pixel = inputBitmap.getPixel(j,i).toInt()
                val green = pixel and 0xFF00FF00.toInt()
                outputBitmap[j, i] = green
            }
        }
        return outputBitmap
    }

    private fun customFilter(inputBitmap: Bitmap) : Array<Bitmap?> {
        val outputBitmap =
            createBitmap(inputBitmap.width, inputBitmap.height, Bitmap.Config.ARGB_8888)
        val outputBitmapRev =
            createBitmap(inputBitmap.width, inputBitmap.height, Bitmap.Config.ARGB_8888)
        for (i in 0 until inputBitmap.height) {
            for (j in 0 until inputBitmap.width) {
                val pixel = inputBitmap.getPixel(j,i).toInt()
                val blue = pixel and 0x000000FF.toInt()
                val green = (pixel and 0x0000FF00.toInt()) shr 8
                if(green < FLUORESCENCE_THRESHOLD * blue){
                    outputBitmap[j, i] = 0
                    outputBitmapRev[j, i] = pixel
                }else{
                    outputBitmap[j, i] = pixel
                    outputBitmapRev[j, i] = 0
                }
            }
        }
        return arrayOf(outputBitmap, outputBitmapRev)
    }
//    private fun customFilterRev(inputBitmap: Bitmap) : Bitmap {
//        val outputBitmap =
//            createBitmap(inputBitmap.width, inputBitmap.height, Bitmap.Config.ARGB_8888)
//        for (i in 0 until inputBitmap.height) {
//            for (j in 0 until inputBitmap.width) {
//                val pixel = inputBitmap.getPixel(j,i).toInt()
//                val blue = pixel and 0x000000FF.toInt()
//                val green = (pixel and 0x0000FF00.toInt()) shr 8
//                if(green >= FLUORESCENCE_THRESHOLD * blue){
//                    outputBitmap[j, i] = 0
//                }else{
//                    outputBitmap[j, i] = pixel
//                }
//            }
//        }
//        return outputBitmap
//    }

    private fun getFilteredBlueMean(inputBitmap: Bitmap) : Double {
        var bluetmp = 0
        var greentmp = 0
        var blueSum = 0.0
        var greenSum = 0.0
        var cnt = 0

        filteredArea = 0.0

        val height = inputBitmap.height
        val width = inputBitmap.width
        for (i in 0 until height) {
            for (j in 0 until width) {
                val pixel = inputBitmap.getPixel(j, i).toInt()
                bluetmp = (0x000000FF.toInt() and pixel)
                greentmp = (0x0000FF00.toInt() and pixel) shr 8
                if(bluetmp != 0){
                    cnt++
                }
                blueSum += bluetmp
                greenSum += greentmp
            }
        }
        return if(cnt!=0) {
            val bluePixelMeanFiltered = blueSum/cnt
            val greenPixelMeanFiltered = greenSum/cnt
            filteredArea = cnt/(width.toDouble() * height.toDouble())
            bluePixelMeanFiltered
        } else{
            0.0
        }
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

    @SuppressLint("ResourceAsColor")
    private fun chartInit(chart : LineChart){
        chart.setDrawGridBackground(false)
        chart.setBackgroundColor(R.color.main_background)
        chart.setGridBackgroundColor(R.color.main_background)
//
        chart.getDescription().setEnabled(false)

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

        val yAxis : YAxis = chart.axisLeft
        yAxis.isEnabled = true
        yAxis.textColor = Color.BLACK
        yAxis.setDrawGridLines(false)

        val lgnd = chart.legend
        lgnd.textSize = 16.0f

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
        val set: LineDataSet = channel.let{ channel ->
            when(channel){
                HISTOGRAM_BLUE_CHANNEL -> LineDataSet(null, "Blue Pixels")
                HISTOGRAM_GREEN_CHANNEL -> LineDataSet(null, "Green Pixels")
                else -> LineDataSet(null, "No Label")
            }
        }
        set.lineWidth = 2f
        set.setDrawValues(false)
        set.valueTextColor = Color.BLACK
        when(channel){
            HISTOGRAM_BLUE_CHANNEL -> {
                set.color = Color.rgb(0x00,0x00,0xEE)
                set.mode = LineDataSet.Mode.LINEAR
            }
            HISTOGRAM_GREEN_CHANNEL -> {
                set.color = Color.rgb(0x00, 0xEE, 0x00)
                set.mode = LineDataSet.Mode.LINEAR
            }

        }
//        when(channel) {
//            HISTOGRAM_BLUE_CHANNEL -> set.color = Color.rgb(0x00,0x00,0xEE)
//            HISTOGRAM_GREEN_CHANNEL -> set.color = Color.rgb(0x00, 0xEE, 0x00)
//        }

        set.setDrawCircles(false)
        set.highLightColor = Color.rgb(190, 190, 190)
        return set
    }

    private fun addEntryGlucose(chart: LineChart, channel : Int, num : Double){
        var data = chart.data
        if (data == null) {
            data = LineData()
            chart.data = data
        }
        var set = data.getDataSetByIndex(channel)
//        var set = data.getDataSetByIndex(channel)
        if (set == null) {
            //set = createSet(channel)
            set = createSetGlucose(channel)
            data.addDataSet(set)
        }

        data.addEntry(Entry(set.entryCount.toFloat(), num.toFloat()), channel)
        data.notifyDataChanged()

        // let the chart know it's data has changed
        chart.notifyDataSetChanged()
        chart.setVisibleXRangeMaximum(data.entryCount.toFloat())
        chart.setVisibleXRange(0f,data.entryCount.toFloat())

        // this automatically refreshes the chartArray[channel] (calls invalidate())
        chart.moveViewTo(data.entryCount.toFloat(), 50f, YAxis.AxisDependency.LEFT)
    }


    private fun removeEntryGlucose(chart : LineChart, idx : Int){
        clearChart(chart)
        if((idx >= glucoseMeanArray.size) or (glucoseMeanArray.size == 1))
            return
        glucoseMeanArray.removeAt(idx)
        glucoseMeanCntArray.removeAt(idx)
        for (element in glucoseMeanArray) {
            addEntryGlucose(chart, GLUCOSE_FILTERED_CHANNEL, element)
        }
    }

    private fun createSetGlucose(channel: Int): LineDataSet {

        var set : LineDataSet? = null
        when(channel){
            GLUCOSE_RAW_CHANNEL-> {
                set = LineDataSet(null, "Raw Blue mean")
                set.color = Color.rgb(0x11, 0x11, 0xF0)
            }
            GLUCOSE_FILTERED_CHANNEL -> {
                set = LineDataSet(null, "Glucose level")
                set.color = Color.rgb(0xF0, 0x5B, 0x53)
//                set.color = R.color.glucose_lineColor
            }
        }
        set!!.lineWidth = 3f
        set.setDrawValues(false)
        set.valueTextColor = Color.BLACK
//        set.color = Color.rgb(0xEB, 0x77, 0x72)
//        set.mode = LineDataSet.Mode.CUBIC_BEZIER

        set.setDrawCircles(false)
        set.highLightColor = Color.rgb(190, 190, 190)
        return set
    }
    companion object {
        private const val PERMISSION_CAMERA = 1000
        private const val RC_CROP_IMAGE = 1001

        private const val REQUEST_CAMERA = 2000

        private const val HISTOGRAM_BLUE_CHANNEL = 0
        private const val HISTOGRAM_GREEN_CHANNEL = 1

        private const val GLUCOSE_FILTERED_CHANNEL = 0
        private const val GLUCOSE_RAW_CHANNEL = 1

        private const val FLUORESCENCE_THRESHOLD = 0.35
        private const val GLUCOSE_HIGH_SEEKBAR = 2
        private const val GLUCOSE_MODERATE_SEEKBAR = 1
        private const val GLUCOSE_LOW_SEEKBAR = 0
        private const val GLUCOSE_HIGH_STRING = "High"
        private const val GLUCOSE_MODERATE_STRING = "Normal"
        private const val GLUCOSE_LOW_STRING = "Low"
        private const val GLUCOSE_HIGH_THRESHOLD = 22
        private const val GLUCOSE_LOW_THRESHOLD = 12
    }
}