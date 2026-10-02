package dev.puckmouse

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Size
import java.nio.file.Path
import java.nio.file.Files
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

private class PuckTrayIcon : Painter() {
    override val intrinsicSize=Size(32f,32f)
    override fun DrawScope.onDraw() {drawCircle(Color(0xffb8d8bf),size.minDimension*.46f);drawCircle(Color(0xff202125),size.minDimension*.25f)}
}
fun main(args: Array<String>) {
    if(args.firstOrNull()=="--self-test") {
        val dll=if(args.size>1 && args[1].endsWith(".dll"))Path.of(args[1]).toAbsolutePath() else defaultDllPath()
        PuckEngine(Profile(),dll).use { e ->
            e.frame(0.0);e.feed(0.0,Axes(rz=.5));val frame=e.frame(16.0)
            check(frame[Output.SCROLL_Y]!!>0);e.interrupt(17.0);e.feed(18.0,Axes(rz=.5));check(e.frame(32.0).values.all{it==0.0})
        }
        val result=args.lastOrNull()?.takeIf{it.endsWith(".txt")}
        if(result!=null)Files.writeString(Path.of(result),"Puck Mouse ${AppVersion.value} bundled runtime / DLL self-test passed")
        println("Puck Mouse ${AppVersion.value} DLL and pause self-test passed");return
    }
    val qa=args.contains("--qa") || System.getProperty("puckmouse.qa")=="true" || System.getenv("PUCK_MOUSE_QA")=="1"
    // A second process must never inject duplicate motion or compete for registrations.
    val lockFile=Path.of(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"),"PuckMouse","instance.lock")
    Files.createDirectories(lockFile.parent)
    val lockChannel=FileChannel.open(lockFile,StandardOpenOption.CREATE,StandardOpenOption.WRITE)
    val lock=if(!qa)lockChannel.tryLock() else null
    if(!qa && lock==null) {javax.swing.JOptionPane.showMessageDialog(null,"Puck Mouse is already running. Open it from the system tray.");lockChannel.close();return}
    val controller=AppController(if(qa)SettingsStore(null) else SettingsStore.default(),allowDesktopOutput=!qa)
    if(qa)controller.setPreview(true)
    try { application {
        var visible by remember{mutableStateOf(true)}
        val state by controller.state.collectAsState()
        val trayAvailable=isTraySupported
        if(trayAvailable && !qa) Tray(icon=PuckTrayIcon(),tooltip="Puck Mouse · ${state.status}",onAction={visible=true}) {
            Item("Open Puck Mouse",onClick={visible=true})
            Item(if(state.paused)"Resume" else "Pause",onClick={controller.togglePause()})
            Separator()
            Item("Quit",onClick={exitApplication()})
        }
        Window(visible=visible,onCloseRequest={if(trayAvailable && state.settings.minimizeToTray && !qa)visible=false else exitApplication()},title="Puck Mouse",
            state=rememberWindowState(width=1160.dp,height=800.dp)) {
            PuckMouseApp(controller)
        }
    } } finally {controller.close();lock?.release();lockChannel.close()}
}
