package com.example.codebase.utils

import android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
import android.Manifest.permission.ACTIVITY_RECOGNITION
import android.Manifest.permission.BLUETOOTH_ADVERTISE
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.Manifest.permission.CAMERA
import android.Manifest.permission.NEARBY_WIFI_DEVICES
import android.Manifest.permission.POST_NOTIFICATIONS
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_AUDIO
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VIDEO
import android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
import android.Manifest.permission.RECORD_AUDIO
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Permission checks and the request launcher need an Activity, so only the pure decisions are tested.
class PermissionUtilsTest {

    private val none = emptyList<String>()

    @Test
    fun `regular permissions apply on every API level`() {
        val permissions = listOf(CAMERA, RECORD_AUDIO)

        listOf(24, 28, 29, 30, 32, 33, 34).forEach { sdk ->
            assertEquals(permissions, applicablePermissions(permissions, sdk))
        }
    }

    @Test
    fun `duplicate permissions are requested once`() {
        assertEquals(listOf(CAMERA), applicablePermissions(listOf(CAMERA, CAMERA), 34))
    }

    @Test
    fun `notification, media and nearby wifi permissions apply from API 33`() {
        val permissions = listOf(
            POST_NOTIFICATIONS, READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_AUDIO, NEARBY_WIFI_DEVICES
        )

        assertEquals(none, applicablePermissions(permissions, 32))
        assertEquals(permissions, applicablePermissions(permissions, 33))
    }

    @Test
    fun `selected photos permission applies from API 34`() {
        val permissions = listOf(READ_MEDIA_VISUAL_USER_SELECTED)

        assertEquals(none, applicablePermissions(permissions, 33))
        assertEquals(permissions, applicablePermissions(permissions, 34))
    }

    @Test
    fun `bluetooth permissions apply from API 31`() {
        val permissions = listOf(BLUETOOTH_SCAN, BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE)

        assertEquals(none, applicablePermissions(permissions, 30))
        assertEquals(permissions, applicablePermissions(permissions, 31))
    }

    @Test
    fun `background location and activity recognition apply from API 29`() {
        val permissions = listOf(ACCESS_BACKGROUND_LOCATION, ACTIVITY_RECOGNITION)

        assertEquals(none, applicablePermissions(permissions, 28))
        assertEquals(permissions, applicablePermissions(permissions, 29))
    }

    @Test
    fun `legacy storage permissions stop applying with scoped storage`() {
        assertEquals(listOf(WRITE_EXTERNAL_STORAGE), applicablePermissions(listOf(WRITE_EXTERNAL_STORAGE), 28))
        assertEquals(none, applicablePermissions(listOf(WRITE_EXTERNAL_STORAGE), 29))
        assertEquals(listOf(READ_EXTERNAL_STORAGE), applicablePermissions(listOf(READ_EXTERNAL_STORAGE), 32))
        assertEquals(none, applicablePermissions(listOf(READ_EXTERNAL_STORAGE), 33))
    }

    @Test
    fun `only the applicable part of a mixed list is kept`() {
        assertEquals(
            listOf(CAMERA),
            applicablePermissions(listOf(CAMERA, POST_NOTIFICATIONS, WRITE_EXTERNAL_STORAGE), 30)
        )
    }

    @Test
    fun `allGranted needs every requested permission granted`() {
        val requested = listOf(CAMERA, RECORD_AUDIO)

        assertTrue(allGranted(requested, mapOf(CAMERA to true, RECORD_AUDIO to true)))
        assertFalse(allGranted(requested, mapOf(CAMERA to true, RECORD_AUDIO to false)))
        assertFalse(allGranted(requested, mapOf(CAMERA to true)))
        assertFalse(allGranted(requested, emptyMap()))
    }

    @Test
    fun `media image permissions depend on the API level`() {
        assertEquals(listOf(READ_EXTERNAL_STORAGE), mediaImagePermissionsFor(32))
        assertEquals(listOf(READ_MEDIA_IMAGES), mediaImagePermissionsFor(33))
        assertEquals(listOf(READ_MEDIA_IMAGES, READ_MEDIA_VISUAL_USER_SELECTED), mediaImagePermissionsFor(34))
    }

    @Test
    fun `media access on API 34 is partial when only selected photos are granted`() {
        assertEquals(MediaAccess.PARTIAL, mediaAccessOf(34) { it == READ_MEDIA_VISUAL_USER_SELECTED })
        assertEquals(
            MediaAccess.FULL,
            mediaAccessOf(34) { it == READ_MEDIA_IMAGES || it == READ_MEDIA_VISUAL_USER_SELECTED }
        )
        assertEquals(MediaAccess.NONE, mediaAccessOf(34) { false })
    }

    @Test
    fun `media access below API 33 depends only on READ_EXTERNAL_STORAGE`() {
        assertEquals(MediaAccess.FULL, mediaAccessOf(32) { it == READ_EXTERNAL_STORAGE })
        assertEquals(MediaAccess.NONE, mediaAccessOf(32) { it == READ_MEDIA_IMAGES })
        assertEquals(MediaAccess.NONE, mediaAccessOf(33) { it == READ_EXTERNAL_STORAGE })
    }
}
