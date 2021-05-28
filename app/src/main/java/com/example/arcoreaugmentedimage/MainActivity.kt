package com.example.arcoreaugmentedimage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.PixelCopy
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ViewRenderable
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.layout_bg.view.*
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException


class MainActivity : AppCompatActivity() {

    private val TAG = MainActivity::class.java.simpleName
    private var installRequested: Boolean = false
    private var session: Session? = null
    private var shouldConfigureSession = false
    private val messageSnackbarHelper = SnackbarHelper()
    internal lateinit var dataView: CompletableFuture<ViewRenderable>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeSceneView()
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) {
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(
                this, "Camera permissions are needed to run this application", Toast.LENGTH_LONG
            )
                .show()
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this)
            }
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus)
    }

    private fun initializeSceneView() {
        makeInfoView()
        arSceneView.scene.setOnUpdateListener(this::onUpdateFrame)
    }

    private fun onUpdateFrame(frameTime: FrameTime) {
        frameTime.toString()
        val frame = arSceneView.arFrame
        val updatedAugmentedImages = frame.getUpdatedTrackables(AugmentedImage::class.java)

        for (augmentedImage in updatedAugmentedImages) {
            if (augmentedImage.trackingState == TrackingState.TRACKING) {
                // Check camera image matches our reference image

                if (augmentedImage.name == "marker") {
                    createRenderable(augmentedImage, "View Renderable Demo");
                }
            }
        }
    }

    private fun setupAugmentedImageDb(config: Config): Boolean {
        val augmentedImageDatabase: AugmentedImageDatabase
        val augmentedImageBitmap = loadAugmentedImage() ?: return false

        augmentedImageDatabase = AugmentedImageDatabase(session)
        augmentedImageDatabase.addImage("marker", augmentedImageBitmap)

        config.augmentedImageDatabase = augmentedImageDatabase
        return true
    }

    private fun loadAugmentedImage(): Bitmap? {
        try {
            assets.open("marker.jpg").use { `is` -> return BitmapFactory.decodeStream(`is`) }
        } catch (e: IOException) {
            Log.e(TAG, "IO exception loading augmented image bitmap.", e)
        }

        return null
    }

    private fun configureSession() {
        val config = Config(session)
        config.focusMode = Config.FocusMode.AUTO
        if (!setupAugmentedImageDb(config)) {
            messageSnackbarHelper.showError(this, "Could not setup augmented image database")
        }
        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        session!!.configure(config)
    }

    override fun onResume() {
        super.onResume()
        if (session == null) {
            var exception: Exception? = null
            var message: String? = null
            try {
                when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        installRequested = true
                        return
                    }
                    ArCoreApk.InstallStatus.INSTALLED -> {
                    }
                }

                if (!CameraPermissionHelper.hasCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this)
                    return
                }

                session = Session(/* context = */this)
            } catch (e: UnavailableArcoreNotInstalledException) {
                message = "Please install ARCore"
                exception = e
            } catch (e: UnavailableUserDeclinedInstallationException) {
                message = "Please install ARCore"
                exception = e
            } catch (e: UnavailableApkTooOldException) {
                message = "Please update ARCore"
                exception = e
            } catch (e: UnavailableSdkTooOldException) {
                message = "Please update this app"
                exception = e
            } catch (e: Exception) {
                message = "This device does not support AR"
                exception = e
            }

            if (message != null) {
                messageSnackbarHelper.showError(this, message)
                Log.e(TAG, "Exception creating session", exception)
                return
            }

            shouldConfigureSession = true
        }

        if (shouldConfigureSession) {
            configureSession()
            shouldConfigureSession = false
            arSceneView.setupSession(session)
        }

        // Note that order matters - see the note in onPause(), the reverse applies here.
        try {
            session!!.resume()
            arSceneView.resume()
        } catch (e: CameraNotAvailableException) {
            messageSnackbarHelper.showError(this, "Camera not available. Please restart the app.")
            session = null
            return
        }

    }


    private fun createRenderable(augmentedImage: AugmentedImage, name : String) {
        var renderable : ViewRenderable?=null
        try {
            renderable = dataView.get()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } catch (e: ExecutionException) {
            e.printStackTrace()
        }
        val node = Node()
        try {

            val anchorNode = AnchorNode(arSceneView.session.createAnchor(augmentedImage.centerPose))
            arSceneView.scene.removeChild(anchorNode)
            val pose = Pose.makeTranslation(0.0f, 0.0f, 0.12f)
            node.localPosition = Vector3(pose.tx(), pose.ty(), pose.tz())

            node.renderable = renderable
            node.setParent(anchorNode)
            node.localRotation = Quaternion(pose.qx(), 90f, -90f, pose.qw())

            arSceneView.scene.addChild(anchorNode)
            setNodeData(renderable!!,name)
            makeInfoView()

        } catch (e: Exception) {
            e.toString()
        }
    }

    fun setNodeData(viewRenderable: ViewRenderable, name: String){
        var view = viewRenderable.view
        view.testId.text = name
        view.greeting1.text = "Welcome to AR"
        view.greeting2.text = "Explore AR in Android"
    }

    fun makeInfoView(){
        dataView = ViewRenderable.builder().setView(this, R.layout.layout_bg).build()
    }
}
