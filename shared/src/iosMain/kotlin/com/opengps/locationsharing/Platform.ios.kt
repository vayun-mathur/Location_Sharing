package com.opengps.locationsharing

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ComposeUIViewController
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.NativeSQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Contacts.CNContact
import platform.ContactsUI.CNContactPickerDelegateProtocol
import platform.ContactsUI.CNContactPickerViewController
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIApplication
import platform.UIKit.UINavigationController
import platform.UIKit.UITabBarController
import platform.UIKit.UIViewController
import platform.darwin.NSObject

class IOSPlatform: Platform() {
    @OptIn(ExperimentalForeignApi::class)
    override val dataStore: DataStore<Preferences> = createDataStore(
    producePath = {
        val documentDirectory: NSURL? = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )
        requireNotNull(documentDirectory).path + "/$dataStoreFileName"
    }
    )
    override val database = Room.databaseBuilder<AppDatabase>(
        name = documentDirectory() + "/my_room.db",
    ).setDriver(BundledSQLiteDriver()).build()

    private val contactPicker = CNContactPickerViewController()

    @Composable
    override fun requestPickContact(callback: (String, String?) -> Unit): () -> Unit {
        return {
            contactPicker.delegate = object : NSObject(), CNContactPickerDelegateProtocol {
                override fun contactPickerDidCancel(picker: CNContactPickerViewController) {
                    contactPicker.delegate = null
                    picker.dismissViewControllerAnimated(true, null)
                }

                override fun contactPicker(
                    picker: CNContactPickerViewController,
                    didSelectContact: CNContact,
                ) {
                    val id = didSelectContact.identifier
                    val name = "${didSelectContact.givenName} ${didSelectContact.familyName}".trim()

                    //TODO: add image
                    println(name)
                    callback(name, null)
                    contactPicker.delegate = null
                    picker.dismissViewControllerAnimated(true, null)

                }
            }

            UIViewController.topMostViewController()
                ?.presentViewController(contactPicker, true, null)
        }
    }

    override fun runBackgroundService() {
        BackgroundService()
    }

    override fun createNotification(s: String, channelId: String) {
        //TODO: implement this later
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(documentDirectory?.path)
}

private fun UIViewController.Companion.topMostViewController(): UIViewController? {
    return findTopMostViewController(UIApplication.sharedApplication.keyWindow?.rootViewController)
}

private fun findTopMostViewController(rootViewController: UIViewController?): UIViewController? {
    if (rootViewController?.presentedViewController == null) {
        return rootViewController
    }

    if (rootViewController.presentedViewController is UINavigationController) {
        val navigationController =
            rootViewController.presentedViewController as UINavigationController
        return navigationController.visibleViewController ?: navigationController
    }

    if (rootViewController.presentedViewController is UITabBarController) {
        val tabBarController = rootViewController.presentedViewController as UITabBarController
        return tabBarController.selectedViewController ?: tabBarController
    }

    return null
}

actual fun getPlatform(): Platform {
    return platformObject_IOS
}

val platformObject_IOS = IOSPlatform()

fun MainViewController(): UIViewController =
    ComposeUIViewController {
        Main()
    }
