package otus.gpb.homework.activities

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar


class EditProfileActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "EditProfileActivity"
        private const val TELEGRAM_PACKAGE = "org.telegram.messenger"
    }

    private lateinit var imageView: ImageView
    private lateinit var imageUri: Uri

    private lateinit var profileName: TextView
    private lateinit var profileSurname: TextView
    private lateinit var profileAge: TextView

    private val actionMakePhoto by lazy {Pair(0, getString(R.string.action_make_photo))}
    private val actionChoosePhoto by lazy {Pair(1, getString(R.string.action_choose_photo))}
    private val addImageActions by lazy {arrayOf(actionMakePhoto.second, actionChoosePhoto.second)}

    private val onClickAddImage = View.OnClickListener {
        MaterialAlertDialogBuilder(this)
            .setTitle(resources.getString(R.string.alert_dialog_title))
            .setItems(addImageActions) { _, option ->
                when (option) {
                    actionMakePhoto.first -> requestPermission()
                    actionChoosePhoto.first -> openGallery()
                }
            }
            .show()
    }

    private val onClickFillForm = View.OnClickListener {
        fillFormLauncher.launch(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)
        imageView = findViewById(R.id.imageview_photo)
        profileName = findViewById(R.id.edit_profile_name)
        profileSurname = findViewById(R.id.edit_profile_surname)
        profileAge = findViewById(R.id.edit_profile_age)

        imageView.setOnClickListener(onClickAddImage)

        findViewById<Toolbar>(R.id.toolbar).apply {
            inflateMenu(R.menu.menu)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.send_item -> {
                        openSenderApp()
                        true
                    }
                    else -> false
                }
            }
        }
        findViewById<Button>(R.id.edit_profile_edit_button).setOnClickListener(onClickFillForm)
    }

    private fun showCat() {
        val rId = R.drawable.cat
        imageView.setImageDrawable(
            ActivityCompat.getDrawable(this, rId)
        )
        // Если доставать изображение из resources по uri, то
        // Telegram выдает "Вложение не поддерживается"
        imageUri = Uri.parse(
            ContentResolver.SCHEME_CONTENT + "://" +
                    resources.getResourcePackageName(rId) + '/' +
                    resources.getResourceTypeName(rId) + '/' +
                    resources.getResourceEntryName(rId) )
    }

    private val openSettingsLauncher = registerForActivityResult(
        object: ActivityResultContract<String, Boolean>() {
            override fun createIntent(context: Context, input: String): Intent {
                return Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            }

            override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
                return ContextCompat.checkSelfPermission(
                        this@EditProfileActivity, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
    ) {
        if (!it) {
            Snackbar.make(imageView, getString(R.string.settings_result_negative),
                Snackbar.LENGTH_SHORT).show()
        } else { showCat() }
    }

    private val requestCameraLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        granted ->
        if (granted) {
            showCat()
        } else if (!ActivityCompat.shouldShowRequestPermissionRationale(
            this, Manifest.permission.CAMERA)) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_alert_title)
                .setPositiveButton(R.string.open_settings) {
                        _,_ -> openSettingsLauncher.launch("")
                }.show()
        }
    }

    private val imgRequestString = "image/*"
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) {  iUri ->
        iUri?.let{
            imageUri = iUri
            populateImage(imageUri)
        }
    }

    private val fillFormLauncher = registerForActivityResult(
        EditProfileContract()
    ) { profile ->
        profile?.let {
            profileName.text = profile.name
            profileSurname.text = profile.surname
            profileAge.text = profile.age.toString()
        }

    }


    private fun requestPermissionWithRationale() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.rationale_title))
            .setMessage(getString(R.string.rationale_message))
            .setPositiveButton(getString(R.string.rationale_positive)) {
                    _, _-> requestCameraLauncher.launch(Manifest.permission.CAMERA)
            }
            .setNegativeButton(getString(R.string.rationale_negative)) {
                    dialog, _ -> dialog.cancel()
            }.show()
    }

    private fun openGallery() {
        galleryLauncher.launch(imgRequestString)
    }

    private fun requestPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            requestPermissionWithRationale()
        } else {
            requestCameraLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    /**
     * Используйте этот метод чтобы отобразить картинку полученную из медиатеки в ImageView
     */
    private fun populateImage(uri: Uri) {
        val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
        imageView.setImageBitmap(bitmap)
    }

    private fun isTelegramInstalled(): Boolean {
        return try {
            packageManager.getPackageInfo(TELEGRAM_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun openSenderApp() {
        if (isTelegramInstalled()) {
            val messageText = "${profileName.text}\n" +
                    "${profileSurname.text}\n" +
                    "${profileAge.text}\n"
            val telegramIntent = Intent(
                Intent.ACTION_SEND
            ).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, messageText)
                .putExtra(Intent.EXTRA_STREAM, imageUri)
                .setPackage(TELEGRAM_PACKAGE)
            startActivity(telegramIntent)
        } else {
            // Т.к. Snackbar производит обход дерева Views в поисках подходящей для отображения,
            // можем передать imageView
            Snackbar.make(imageView, getString(R.string.no_telegram_alert_message)
                , Snackbar.LENGTH_LONG).show()
        }
    }
}