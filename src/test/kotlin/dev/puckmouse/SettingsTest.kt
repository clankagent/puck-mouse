package dev.puckmouse

import org.junit.Test
import kotlin.test.*
import java.nio.file.Files

class SettingsTest {
    @Test fun replacingInvalidSettingsPreservesTheOriginalAndImportDoesNotChangePreferences() {
        val dir=Files.createTempDirectory("puckmouse-recovery-");val file=dir.resolve("settings.json")
        try {
            Files.writeString(file,"broken settings")
            val store=SettingsStore(file);store.load();store.save(Settings())
            val backup=Files.list(dir).use{it.filter{p -> p.fileName.toString().startsWith("settings.invalid-")}.findFirst().orElseThrow()}
            assertEquals("broken settings",Files.readString(backup));assertNull(SettingsStore(file).loadError)
            assertEquals(1,store.read(file).profiles.size)
            AppController(SettingsStore(null),native=false).use { controller ->
                val before=controller.state.value.settings.hotkey
                store.write(file,Settings(hotkey=Hotkey(key=0x78,alt=false,shift=true)))
                controller.importProfiles(file)
                assertEquals(2,controller.state.value.settings.profiles.size)
                assertEquals(before,controller.state.value.settings.hotkey)
            }
        } finally {Files.list(dir).use{it.forEach(Files::deleteIfExists)};Files.deleteIfExists(dir)}
    }
    @Test fun settingsRoundTripAndImportValidateBounds() {
        val dir=Files.createTempDirectory("puckmouse-test-");val file=dir.resolve("settings.json")
        try {
            val store=SettingsStore(file);val s=Settings()
            store.save(s);assertEquals(s,store.load())
            assertFails{validateSettings(s.copy(profiles=listOf(s.profiles.first().copy(mappings=s.profiles.first().mappings.map{it.copy(deadzone=.99)}))))}
            assertFails{validateSettings(s.copy(hotkey=Hotkey(key=0x7b)))}
            assertFails{validateSettings(s.copy(hotkey=Hotkey(key=0x3a)))}
            assertFails{validateSettings(s.copy(profiles=s.profiles+s.profiles))}
            Files.writeString(file,"x".repeat(65537));assertFails{store.read(file)}
            store.load();assertNotNull(store.loadError);assertEquals(65537,Files.size(file).toInt())
        } finally {Files.deleteIfExists(file);Files.deleteIfExists(dir)}
    }
    @Test fun profilesRemainIndependentAndAtLeastOneSurvives() {
        AppController(SettingsStore(null),native=false).use{c ->
            val original=c.state.value.profile
            c.duplicateProfile("Reading");val reading=c.state.value.profile
            assertNotEquals(original.id,reading.id)
            c.updateMapping(reading.mappings[2].copy(axis=Axis.TILT_X))
            c.selectProfile(original.id);assertEquals(Axis.TWIST,c.state.value.profile.mappings[2].axis)
            c.deleteProfile();assertEquals(1,c.state.value.settings.profiles.size)
            c.deleteProfile();assertNotNull(c.state.value.error);assertEquals(1,c.state.value.settings.profiles.size)
        }
    }
}
