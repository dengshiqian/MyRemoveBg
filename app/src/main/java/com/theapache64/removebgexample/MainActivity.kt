package com.theapache64.removebgexample

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.*
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.OnClick
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.esafirm.imagepicker.features.ImagePicker
import com.karumi.dexter.Dexter
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.single.BasePermissionListener
import com.karumi.dexter.listener.single.CompositePermissionListener
import com.karumi.dexter.listener.single.DialogOnDeniedPermissionListener
import com.theapache64.removebg.RemoveBg
import com.theapache64.removebg.utils.ErrorResponse
import com.theapache64.twinkill.logger.info
import java.io.File
import java.lang.StringBuilder


class MainActivity : AppCompatActivity() {

    private val projectDir by lazy {
        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        File("$rootPath/remove-bg")
    }
    private var inputImage: File? = null
    private var outputImage: File? = null


    @BindView(R.id.iv_input)
    lateinit var ivInput: ImageView

    @BindView(R.id.iv_output)
    lateinit var ivOutput: ImageView

    @BindView(R.id.tv_input_details)
    lateinit var tvInputDetails: TextView

    @BindView(R.id.b_process)
    lateinit var bProcess: View

    @BindView(R.id.tv_progress)
    lateinit var tvProgress: TextView

    @BindView(R.id.pb_progress)
    lateinit var pbProgress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ButterKnife.bind(this)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

    }

    @OnClick(R.id.b_choose_image, R.id.i_choose_image)
    fun onChooseImageClicked() {

        info("Choose inputImage clicked")

        ImagePicker.create(this)
            .single()
            .start()
    }

    private fun appendInputDetails(details: String) {
        tvInputDetails.text = "${tvInputDetails.text}\n$details"
    }

    private fun clearInputDetails() {
        tvInputDetails.text = ""
    }

    @OnClick(R.id.iv_input)
    fun onInputClicked() {
        if (inputImage != null) {
            viewImage(inputImage!!)
        } else {
            toast(R.string.error_no_image_selected)
        }
    }

    @OnClick(R.id.iv_output)
    fun onOutputClicked() {
        if (outputImage != null) {
            viewImage(outputImage!!)
        } else {
            toast(R.string.error_output_not_saved)
        }
    }

//    private fun viewImage(inputImage: File) {
//
//        val uri = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.provider", inputImage)
//
//        Intent(Intent.ACTION_VIEW).apply {
//            setDataAndType(uri, "image/*")
//            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
//            startActivity(this)
//        }
//    }
    private fun viewImage(inputImage: File) {

    // val uri = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.provider", inputImage)
    // 注释掉上面这行代码，因为您不需要使用 FileProvider 来获取图片的 URI

    // Intent(Intent.ACTION_VIEW).apply {
    //     setDataAndType(uri, "image/*")
    //     addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    //     startActivity(this)
    // }
    // 注释掉上面这段代码，因为您不需要使用 Intent 来查看单个图片

        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse("file:///") // 这里设置根目录的 URI
        startActivity(intent) // 这里启动一个文件浏览器或者其他支持该 action 的应用来查看根目录下的图片文件
    }

    @OnClick(R.id.b_process)
    fun onProcessClicked() {
        if (inputImage != null) {

            info("Image is ${inputImage!!.path}")

            // Check permission
            checkPermission {

                info("Permission granted")

                // permission granted, compress the inputImage now
                compressImage(inputImage!!) { bitmap ->

                    info("Image compressed")

                    saveImage("${System.currentTimeMillis()}", bitmap) { compressedImage ->

                        info("Compressed inputImage saved to ${compressedImage.absolutePath}, and removing bg...")
                        val compressedImageSize = compressedImage.length() / 1024
                        val originalImageSize = inputImage!!.length() / 1024

                        pbProgress.visibility = View.VISIBLE
                        tvProgress.visibility = View.VISIBLE

                        tvProgress.setText(R.string.status_uploading)
                        pbProgress.progress = 0

                        val finalImage = if (compressedImageSize < originalImageSize) compressedImage else inputImage!!
                        appendInputDetails("处理后大小 : ${finalImage.length() / 1024}KB")

                        // inputImage saved, now upload
                        RemoveBg.from(finalImage, object : RemoveBg.RemoveBgCallback {

                            override fun onProcessing() {
                                runOnUiThread {
                                    tvProgress.setText(R.string.status_processing)
                                }
                            }

                            override fun onUploadProgress(progress: Float) {
                                runOnUiThread {
                                    tvProgress.text = "Uploading ${progress.toInt()}%"
                                    pbProgress.progress = progress.toInt()
                                }
                            }

                            override fun onError(errors: List<ErrorResponse.Error>) {
                                runOnUiThread {
                                    val errorBuilder = StringBuilder()
                                    errors.forEach {
                                        //errorBuilder.append("${it.title} : ${it.detail} : ${it.code}\n")
                                        errorBuilder.append("${it.title}:${it.code}\n")
                                    }
                                    val error="不支持该图片格式，请先将图片转为: .jpg/.png/.jpeg"
                                    if(errorBuilder.toString().equals("Invalid file type:invalid_file_type\n")){




                                        showErrorAlert(error)
                                        tvProgress.text = errorBuilder.toString()
                                        pbProgress.visibility = View.INVISIBLE
                                    }
                                    else{

                                        val error="其他未知错误！"
                                        showErrorAlert(error)
                                        tvProgress.text = errorBuilder.toString()
                                        pbProgress.visibility = View.INVISIBLE
                                    }
                                }
                            }

                            override fun onSuccess(bitmap: Bitmap) {
                                info("background removed from bg , and output is $bitmap")
                                runOnUiThread {
                                    ivOutput.setImageBitmap(bitmap)
                                    ivOutput.visibility = View.VISIBLE
                                    tvProgress.visibility = View.INVISIBLE
                                    pbProgress.visibility = View.INVISIBLE

                                    // Save output image
                                    saveImage("${inputImage!!.name}-no-bg", bitmap) {
                                        outputImage = it
                                    }
                                }
                            }

                        })
                    }
                }
            }

        } else {
            toast(R.string.error_no_image_selected)
        }
    }

    /**
     * To show an alert message with title 'Error'
     */
    private fun showErrorAlert(message: String) {
        AlertDialog.Builder(this)
            .setTitle("错误!")

            .setMessage(message)
            .create()
            .show()
            //appendInputDetails(message)
    }


    /**
     * To save given bitmap into a file
     */
    private fun saveImage(fileName: String, bitmap: Bitmap, onSaved: (file: File) -> Unit) {

        // Create project dir
        if (!projectDir.exists()) {
            projectDir.mkdir()
        }

        // Create inputImage file
        val imageFile = File("$projectDir/$fileName.png")
        imageFile.outputStream().use { out ->
            //bitmap.compress(Bitmap.CompressFormat.WEBP, 100, out);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }

        onSaved(imageFile)
    }

    /**
     * To compress given inputImage file with Glide
     */


    private fun compressImage(image: File, onLoaded: (bitmap: Bitmap) -> Unit) {

        Glide.with(this)
            .asBitmap()
            .load(image)
            .skipMemoryCache(true) // 禁用内存缓存
            .diskCacheStrategy(DiskCacheStrategy.NONE) // 禁用磁盘缓存
            .into(object : CustomTarget<Bitmap>() {
                override fun onLoadCleared(placeholder: Drawable?) {

                }

                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    onLoaded(resource)
                }
            })
    }
    /**
     * To check WRITE_EXTERNAL_STORAGE permission
     */
    private fun checkPermission(onPermissionChecked: () -> Unit) {

        val deniedListener = DialogOnDeniedPermissionListener.Builder.withContext(this)
            .withTitle(R.string.title_permission)
            .withMessage(R.string.message_permission)
            .withButtonText(R.string.action_ok)
            .build()

        val permissionListener = object : BasePermissionListener() {
            override fun onPermissionGranted(response: PermissionGrantedResponse?) {
                onPermissionChecked()
            }

            override fun onPermissionDenied(response: PermissionDeniedResponse?) {
                toast(R.string.error_permission)
            }
        }

        val listener = CompositePermissionListener(permissionListener, deniedListener)

        Dexter.withActivity(this)
            .withPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            .withListener(listener)
            .check()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (ImagePicker.shouldHandle(requestCode, resultCode, data)) {

            // IMAGE PICKED!!
            val imagePicked = ImagePicker.getFirstImageOrNull(data)

            if (imagePicked != null) {

                this.inputImage = File(imagePicked.path)

                ivInput.visibility = View.VISIBLE

                Glide.with(this)
                    .load(this.inputImage)
                    .into(ivInput)

                // Showing process button
                bProcess.visibility = View.VISIBLE

                clearInputDetails()
                appendInputDetails("图片名称 : ${inputImage!!.name}")
                appendInputDetails("图片大小 : ${inputImage!!.length() / 1024}KB")
                ivOutput.visibility = View.INVISIBLE

            } else {
                toast(R.string.error_no_image_selected)
            }

            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun toast(@StringRes message: Int) {
        toast(getString(message))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
