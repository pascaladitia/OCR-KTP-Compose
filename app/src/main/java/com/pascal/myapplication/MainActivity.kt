package com.pascal.myapplication

import KTPModel
import OCRforEKTPLibrary
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val galleryResultLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { loadBitmapInBackground(it) }
        }

    private val cameraResultLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess: Boolean ->
            if (isSuccess) {
                capturedImageUri?.let { loadBitmapInBackground(it) }
            }
        }

    private var capturedImageUri: Uri? = null
    private var onBitmapAndKTPModelLoaded: ((Bitmap, KTPModel) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HomeScreen()
        }
    }

    @Composable
    fun HomeScreen() {
        var selectedImage by remember { mutableStateOf<Uri?>(null) }
        var bitmap by remember { mutableStateOf<Bitmap?>(null) }
        var ktpModel by remember { mutableStateOf<KTPModel?>(null) }
        var showLoading by remember { mutableStateOf(false) }

        // Set the onBitmapAndKTPModelLoaded callback to update bitmap and KTPModel state
        onBitmapAndKTPModelLoaded = { loadedBitmap, model ->
            bitmap = loadedBitmap
            ktpModel = model
            showLoading = false
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Button to open gallery
            Button(
                onClick = {
                    galleryResultLauncher.launch("image/*")
                    showLoading = true
                }) {
                Text("Pick Image from Gallery")
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Button to open camera
            Button(onClick = {
                val outputUri = createImageUri()
                capturedImageUri = outputUri
                cameraResultLauncher.launch(outputUri)
                showLoading = true
            }) {
                Text("Take Picture from Camera")
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Show loading spinner or selected image and KTP data
            if (showLoading) {
                CircularProgressIndicator()
            } else {
                bitmap?.let { loadedBitmap ->
                    Image(
                        bitmap = loadedBitmap.asImageBitmap(),
                        contentDescription = "Processed Image",
                        modifier = Modifier
                            .fillMaxWidth(),
                        contentScale = ContentScale.Fit
                    )
                }

                Column{
                    ktpModel?.let { model ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("NIK: ${model.nik}")
                        Text("Nama: ${model.nama}")
                        Text("Tempat Lahir: ${model.tempatLahir}")
                        Text("Tanggal Lahir: ${model.tglLahir}")
                        Text("Jenis Kelamin: ${model.jenisKelamin}")
                        Text("Alamat: ${model.alamat}")
                        Text("RT/RW: ${model.rtrw}")
                        Text("Kelurahan: ${model.kelurahan}")
                        Text("Kecamatan: ${model.kecamatan}")
                        Text("Agama: ${model.agama}")
                        Text("Status Perkawinan: ${model.statusPerkawinan}")
                        Text("Pekerjaan: ${model.pekerjaan}")
                        Text("Kewarganegaraan: ${model.kewarganegaraan}")
                        Text("Berlaku Hingga: ${model.berlakuHingga}")
                        Text("Provinsi: ${model.provinsi}")
                        Text("Kab/Kota: ${model.kabKot}")
                        Text("Confidence: ${model.confidence}%")
                    }
                }
            }
        }
    }

    // Create URI for camera capture
    private fun createImageUri(): Uri {
        val contentValues = android.content.ContentValues().apply {
            put(MediaStore.Images.Media.TITLE, "New Photo")
            put(MediaStore.Images.Media.DESCRIPTION, "Captured using camera")
        }
        return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)!!
    }

    // Function to load bitmap in the background and process the image
    private fun loadBitmapInBackground(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Load bitmap in background thread
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)

                // Process the image using OCR and update bitmap and KTPModel in UI
                processKTPImage(bitmap)
            } catch (e: Exception) {
                // Switch to main thread to show error toast
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to load image: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // Function to process the selected image using OCR in a background thread
    private fun processKTPImage(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.IO).launch {
            val ocrLibrary = OCRforEKTPLibrary()

            try {
                // Perform OCR in background
                val ktpModel = ocrLibrary.scanEKTP(bitmap)

                // Switch to main thread to show the result and update UI
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "KTP Data: ${ktpModel.nik}",
                        Toast.LENGTH_LONG
                    ).show()
                    onBitmapAndKTPModelLoaded?.invoke(
                        bitmap,
                        ktpModel
                    ) // Update bitmap and KTPModel in HomeScreen
                }
            } catch (e: Exception) {
                // Switch to main thread to show error
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error processing KTP: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
