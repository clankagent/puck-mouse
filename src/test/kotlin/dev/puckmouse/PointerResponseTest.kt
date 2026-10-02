package dev.puckmouse

import org.junit.Test
import kotlin.test.*
import kotlin.math.*
import java.nio.file.Files
import java.nio.file.Path

class PointerResponseTest {
    private val dll=Path.of(System.getProperty("puck.dll"))

    @Test fun radialResponsePreservesArbitraryAnglesAndSpeedAcrossQuadrants() {
        val profile=Profile()
        PuckEngine(profile,dll).use { engine ->
            engine.frame(0.0)
            var time=0.0
            for(push in listOf(.05,.1,.3,.6,1.0)) for(degrees in listOf(0,7,15,30,45,70,90,130,177,220,285,350)) {
                val angle=Math.toRadians(degrees.toDouble())
                engine.feed(++time,Axes(x=push*cos(angle),y=-push*sin(angle)))
                val rate=engine.rates();val x=rate[Output.POINTER_X]!!;val y=rate[Output.POINTER_Y]!!
                val speed=6000*((push-.04)/.96).coerceIn(0.0,1.0).pow(1.7)
                assertEquals(speed,hypot(x,y),1e-7,"Speed at $degrees°, push $push")
                assertEquals(cos(angle),x/speed,1e-8)
                assertEquals(sin(angle),y/speed,1e-8)
            }
        }
    }

    @Test fun radialDeadZoneHasNoSquareCornersAndDiagonalSpeedIsCapped() {
        PuckEngine(Profile(),dll).use { engine ->
            engine.frame(0.0)
            engine.feed(1.0,Axes(x=.028,y=-.028))
            assertEquals(0.0,engine.rates()[Output.POINTER_X])
            engine.feed(2.0,Axes(x=.035,y=-.035))
            assertTrue(engine.rates()[Output.POINTER_X]!!>0)
            engine.feed(3.0,Axes(x=1.0,y=-1.0))
            val rates=engine.rates()
            assertEquals(6000.0,hypot(rates[Output.POINTER_X]!!,rates[Output.POINTER_Y]!!),1e-8)
        }
    }

    @Test fun independentModeReproducesTheOldAxisAttractionAndCanBeCompared() {
        val angle=Math.toRadians(30.0)
        val mappings=defaultMappings().map {if(it.output.name.startsWith("POINTER"))pointerTravelTuning(it).copy(responseMs=0.0)else it}
        val independent=Profile(mappings=mappings,radialPointer=false)
        val radial=independent.copy(radialPointer=true)
        fun direction(p: Profile): Double = PuckEngine(p,dll).use { e ->
            e.frame(0.0);e.feed(1.0,Axes(x=.5*cos(angle),y=-.5*sin(angle)))
            val r=e.rates();Math.toDegrees(atan2(r[Output.POINTER_Y]!!,r[Output.POINTER_X]!!))
        }
        assertTrue(direction(independent)<10.0)
        assertEquals(30.0,direction(radial),1e-8)
    }

    @Test fun tiltInversionDisabledOutputsAndSharedScrollRemainIndependent() {
        val profile=Profile(mappings=defaultMappings().map {when(it.output) {
            Output.POINTER_X -> it.copy(axis=Axis.TILT_Y,inverted=true)
            Output.POINTER_Y -> it.copy(axis=Axis.TILT_X,inverted=false)
            Output.SCROLL_Y -> it.copy(axis=Axis.TILT_X,responseMs=0.0)
            else -> it
        }})
        PuckEngine(profile,dll).use { e ->
            e.frame(0.0);e.feed(1.0,Axes(rx=.3,ry=-.4))
            val r=e.rates()
            assertEquals(.75,r[Output.POINTER_Y]!!/r[Output.POINTER_X]!!,1e-8)
            assertEquals(12*((.3-.14)/.86).pow(1.7),r[Output.SCROLL_Y]!!,1e-8)
        }
        val disabled=profile.copy(mappings=profile.mappings.map{if(it.output==Output.POINTER_X)it.copy(enabled=false)else it})
        val targets=shapedOutputs(disabled,Axes(rx=.3,ry=1.0))
        assertEquals(0.0,targets[0]);assertEquals(shapedPush(.3,disabled.mappings[1]),targets[1],1e-8)
    }

    @Test fun shapedZeroCannotReplaceActualNeutralAfterInterrupt() {
        val profile=Profile(mappings=defaultMappings().map{it.copy(deadzone=.2)})
        PuckEngine(profile,dll).use { e ->
            e.frame(0.0);e.feed(1.0,Axes(x=.5));e.interrupt(2.0)
            // Inside the response dead zone, but the physical cap is not centered.
            e.feed(3.0,Axes(x=.1));e.feed(4.0,Axes(x=.5))
            assertTrue(e.frame(12.0).values.all{it==0.0})
            e.feed(13.0,Axes());e.feed(14.0,Axes(x=.5))
            assertTrue(e.frame(22.0)[Output.POINTER_X]!!>0)
        }
    }

    @Test fun velocityIntegrationIsIndependentOfReportCadenceAndKeepsTinyFractions() {
        fun travel(reportEvery: Int): Double = PuckEngine(Profile(),dll).use { e ->
            e.frame(0.0);e.feed(0.0,Axes(x=.3,y=-.4))
            var total=0.0
            for(t in 1..1000) {
                if(t%reportEvery==0)e.feed(t.toDouble(),Axes(x=.3,y=-.4))
                if(t%8==0)total+=e.frame(t.toDouble())[Output.POINTER_X]!!
            }
            total
        }
        assertEquals(travel(4),travel(20),1e-7)
        val accumulator=OutputAccumulator()
        var pixels=0
        repeat(1000) {pixels+=accumulator.take(mapOf(Output.POINTER_X to .001))[Output.POINTER_X]!!}
        assertEquals(1,pixels)
    }

    @Test fun oldFactorySettingsMigrateButCustomTuningAndShortcutsSurvive() {
        val file=Files.createTempFile("puck-natural-migration-",".json")
        try {
            val store=SettingsStore(file)
            Files.writeString(file,"""{"schema":2,"profiles":[{"id":"old","name":"Saved"}],"selectedId":"old"}""")
            val migrated=store.read(file)
            assertEquals(3,migrated.schema);assertEquals(Hotkey(),migrated.hotkey)
            assertEquals(0x13,migrated.hotkey.key);assertEquals(0x4000,migrated.hotkey.modifiers)
            assertEquals(defaultMappings().take(2),migrated.profiles[0].mappings.take(2))
            assertTrue(migrated.profiles[0].linkPointerTuning);assertTrue(migrated.profiles[0].radialPointer)
            val custom=migrated.copy(profiles=listOf(migrated.profiles[0].copy(mappings=migrated.profiles[0].mappings.map {
                if(it.output.name.startsWith("POINTER"))it.copy(speed=8500.0,curve=2.2,responseMs=10.0)else it
            })),hotkey=Hotkey(0x78,true,false,true))
            store.write(file,custom)
            Files.writeString(file,Files.readString(file).replace("\"schema\": 3","\"schema\": 2"))
            assertEquals(custom,store.read(file))
            // Legacy partial objects still use the legacy modifier defaults.
            Files.writeString(file,"""{"schema":1,"hotkey":{"key":120,"alt":false},"profiles":[{"id":"old","name":"Saved"}],"selectedId":"old"}""")
            assertEquals(Hotkey(0x78,true,false,false),store.read(file).hotkey)
            AppController(SettingsStore(null),native=false).use { c ->
                c.updateMapping(c.state.value.profile.mappings[0].copy(axis=Axis.TILT_Y,inverted=true,enabled=false))
                val before=c.state.value.profile
                c.applyNaturalPointerPreset()
                assertEquals(before.mappings,c.state.value.profile.mappings)
                c.setRadialPointer(false);c.exportProfiles(file)
                assertFalse(store.read(file).profiles[0].radialPointer)
            }
        } finally {Files.deleteIfExists(file)}
    }
}
