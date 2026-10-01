package dev.puckmouse

import org.junit.Test
import kotlin.test.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.pow

class TuningTest {
    @Test fun linkingCopiesSelectedTuningAndPreservesAssignmentsAndDirection() {
        AppController(SettingsStore(null),native=false).use {c ->
            c.updateMapping(c.state.value.profile.mappings[0].copy(speed=8500.0,curve=5.0,fullSpeedAt=.6))
            val vertical=c.state.value.profile.mappings[1]
            c.setTuningLinked(Output.POINTER_X,true)
            assertTrue(c.state.value.profile.linkPointerTuning)
            assertEquals(vertical.withTuningFrom(c.state.value.profile.mappings[0]),c.state.value.profile.mappings[1])
            c.updateMapping(c.state.value.profile.mappings[1].copy(axis=Axis.TILT_X,inverted=false,enabled=false,curve=8.0))
            val horizontal=c.state.value.profile.mappings[0]
            assertEquals(8.0,horizontal.curve);assertEquals(Axis.SLIDE_X,horizontal.axis);assertTrue(horizontal.enabled);assertFalse(horizontal.inverted)
            c.setTuningLinked(Output.POINTER_Y,false)
            c.updateMapping(c.state.value.profile.mappings[1].copy(speed=7000.0))
            assertEquals(8500.0,c.state.value.profile.mappings[0].speed)
            c.setTuningLinked(Output.SCROLL_Y,true)
            assertEquals(12.0,c.state.value.profile.mappings[3].speed);assertFalse(c.state.value.profile.mappings[3].enabled)
            c.duplicateProfile("Linked reading");assertTrue(c.state.value.profile.linkScrollTuning)
            c.resetProfile();assertFalse(c.state.value.profile.linkScrollTuning);assertEquals(defaultMappings(),c.state.value.profile.mappings)
        }
    }
    @Test fun linkedProfilesRoundTripAndRejectConflictingImportedTuning() {
        val file=Files.createTempFile("puck-tuning-roundtrip-",".json")
        try {
            val store=SettingsStore(file)
            AppController(store,native=false).use { c ->
                c.setTuningLinked(Output.POINTER_X,true)
                c.updateMapping(c.state.value.profile.mappings[1].copy(curve=12.0,fullSpeedAt=.55,speed=20000.0))
                c.exportProfiles(file)
                assertEquals(c.state.value.settings,store.read(file))
                val invalid=c.state.value.profile.copy(mappings=c.state.value.profile.mappings.map{if(it.output==Output.POINTER_X)it.copy(curve=3.0)else it})
                assertFails{validateSettings(Settings(profiles=listOf(invalid)))}
            }
            val profiles=(1..32).map{Profile(id="profile-$it",name="Profile $it")}
            val full=Settings(profiles=profiles)
            store.write(file,full);assertEquals(full,store.read(file))
        } finally {Files.deleteIfExists(file)}
    }
    @Test fun versionOneOmittedDefaultsAndExplicitValuesSurviveMigration() {
        val file=Files.createTempFile("puck-tuning-migration-",".json")
        try {
            Files.writeString(file,"""{"profiles":[{"id":"old","name":"Everyday"}],"selectedId":"old"}""")
            val store=SettingsStore(file);val old=store.read(file)
            assertEquals(2,old.schema);assertEquals(legacyMappings(),old.profiles[0].mappings);assertFalse(old.profiles[0].linkPointerTuning)
            Files.writeString(file,"""{"schema":1,"profiles":[{"id":"old","name":"Custom","mappings":[{"output":"POINTER_X","axis":"SLIDE_X","speed":1700.0,"curve":2.0},{"output":"POINTER_Y","axis":"SLIDE_Y","inverted":true},{"output":"SCROLL_Y","axis":"TWIST","speed":15.0},{"output":"SCROLL_X","axis":"TILT_Y","enabled":false,"speed":10.0}]}],"selectedId":"old"}""")
            val custom=store.read(file)
            assertEquals(1700.0,custom.profiles[0].mappings[0].speed);assertEquals(900.0,custom.profiles[0].mappings[1].speed)
            assertEquals(1.0,custom.profiles[0].mappings[0].fullSpeedAt)
            store.write(file,custom);assertEquals(custom,store.read(file))
            assertTrue(Files.readString(file).contains("fullSpeedAt"))
        } finally {Files.deleteIfExists(file)}
    }
    @Test fun releasedDllMatchesTheExpandedCurveAndSaturationForSharedAxes() {
        val first=Mapping(Output.POINTER_X,Axis.SLIDE_X,speed=10000.0,deadzone=.1,curve=8.0,responseMs=0.0,fullSpeedAt=.55)
        val second=first.copy(output=Output.POINTER_Y,curve=2.6,fullSpeedAt=.7,inverted=true)
        val profile=Profile(mappings=defaultMappings().map{when(it.output){Output.POINTER_X->first;Output.POINTER_Y->second;else->it}})
        PuckEngine(profile,Path.of(System.getProperty("puck.dll"))).use {engine ->
            engine.frame(0.0)
            for((index,amount) in listOf(0.0,.1,.2,.4,.55,.7,1.0,-.4,-1.0).withIndex()) {
                engine.feed(index.toDouble(),Axes(x=amount))
                val rates=engine.rates()
                for(mapping in listOf(first,second)) {
                    val normalized=((kotlin.math.abs(amount)-mapping.deadzone)/(mapping.fullSpeedAt-mapping.deadzone)).coerceIn(0.0,1.0)
                    val expected=normalized.pow(mapping.curve)*mapping.speed*(if(amount<0)-1 else 1)*(if(mapping.inverted)-1 else 1)
                    assertEquals(expected,rates[mapping.output]!!,1e-7)
                }
            }
            engine.interrupt(20.0);engine.feed(21.0,Axes(x=.7));assertTrue(engine.frame(30.0).values.all{it==0.0})
            engine.feed(31.0,Axes());engine.feed(32.0,Axes(x=.7));assertEquals(10000.0,engine.rates()[Output.POINTER_X]!!,1e-7)
        }
    }
    @Test fun newPointerDefaultsCombineFineControlAndFastTravel() {
        val mapping=defaultMappings()[0]
        fun rate(push:Double)=((push-mapping.deadzone)/(mapping.fullSpeedAt-mapping.deadzone)).coerceIn(0.0,1.0).pow(mapping.curve)*mapping.speed
        assertTrue(rate(.1)<2.0);assertTrue(rate(.4)>1000.0);assertTrue(rate(.6)>3500.0);assertEquals(6000.0,rate(.7))
        assertFails{validateSettings(Settings(profiles=listOf(Profile(mappings=defaultMappings().map{it.copy(curve=13.0)}))))}
        assertFails{validateSettings(Settings(profiles=listOf(Profile(mappings=defaultMappings().map{it.copy(fullSpeedAt=.1,deadzone=.1)}))))}
    }
}
