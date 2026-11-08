package com.amrbendari.googlefileplus

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.activity.compose.BackHandler
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import android.provider.MediaStore
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { GoogleFilePlusApp() } }
}
private enum class Destination { HOME, BROWSE, SPACE, TRASH, SETTINGS }
private enum class NavigationStyle { FLOATING, STANDARD }
private enum class ThemeChoice { SYSTEM, LIGHT, DARK }
private enum class AppLanguage { SYSTEM, ENGLISH, ARABIC }

@Composable private fun GoogleFilePlusApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("google_file_plus_settings", Context.MODE_PRIVATE) }
    var destination by remember { mutableStateOf(Destination.HOME) }
    var navStyle by remember { mutableStateOf(enumPref(prefs.getString("navigation_style","FLOATING"), NavigationStyle.FLOATING)) }
    var theme by remember { mutableStateOf(enumPref(prefs.getString("theme_choice","SYSTEM"), ThemeChoice.SYSTEM)) }
    var language by remember { mutableStateOf(enumPref(prefs.getString("app_language","SYSTEM"), AppLanguage.SYSTEM)) }
    var alpha by remember { mutableFloatStateOf(prefs.getFloat("glass_alpha",.78f)) }
    var spacing by remember { mutableFloatStateOf(prefs.getFloat("nav_spacing",8f)) }
    var navBlur by remember { mutableFloatStateOf(prefs.getFloat("nav_blur",0f)) }
    var navWidth by remember { mutableFloatStateOf(prefs.getFloat("nav_width",92f)) }
    var lastBack by remember { mutableLongStateOf(0L) }
    var refresh by remember { mutableIntStateOf(0) }
    val unlocked = remember { mutableStateOf(false) }
    val dark = when(theme){ThemeChoice.SYSTEM->androidx.compose.foundation.isSystemInDarkTheme();ThemeChoice.LIGHT->false;ThemeChoice.DARK->true}
    val arabic = when(language){AppLanguage.ARABIC->true;AppLanguage.ENGLISH->false;AppLanguage.SYSTEM->Locale.getDefault().language=="ar"}
    val scheme = if(Build.VERSION.SDK_INT>=31){if(dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)}else{if(dark) darkColorScheme() else lightColorScheme()}
    BackHandler {
        if (destination != Destination.HOME) { if(destination==Destination.SPACE) unlocked.value=false; destination=Destination.HOME }
        else { val now=System.currentTimeMillis(); if(now-lastBack<1800L) (context as? ComponentActivity)?.finish() else { lastBack=now; android.widget.Toast.makeText(context,if(arabic) "اضغط رجوع مرة أخرى للخروج" else "Press back again to exit",android.widget.Toast.LENGTH_SHORT).show() } }
    }
    LaunchedEffect(Unit) { cleanupTrash(context) }
    val access = remember(refresh) { if(Build.VERSION.SDK_INT>=30) Environment.isExternalStorageManager() else ContextCompat.checkSelfPermission(context,Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){refresh++}
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){refresh++}
    fun requestAccess(){ if(Build.VERSION.SDK_INT>=30){val i=Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:${context.packageName}"));try{settings.launch(i)}catch(_:Exception){settings.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))}}else permission.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE)) }
    CompositionLocalProvider(LocalLayoutDirection provides if(arabic) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr){
        MaterialTheme(colorScheme=scheme){ Surface(Modifier.fillMaxSize(),color=scheme.background){ Box(Modifier.fillMaxSize()){
            Column(Modifier.fillMaxSize().padding(horizontal=18.dp,vertical=20.dp)){
                Text("Google File+",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                Text(destLabel(destination,arabic),style=MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                when(destination){
                    Destination.HOME,Destination.BROWSE->FileBrowser(context,access,::requestAccess,refresh,{refresh++},arabic,destination==Destination.HOME)
                    Destination.SPACE->SpaceScreen(context,prefs,unlocked.value,{unlocked.value=it},{refresh++},arabic)
                    Destination.TRASH->TrashScreen(context,refresh,{refresh++},arabic)
                    Destination.SETTINGS->SettingsScreen(navStyle,{navStyle=it;prefs.edit().putString("navigation_style",it.name).apply()},theme,{theme=it;prefs.edit().putString("theme_choice",it.name).apply()},language,{language=it;prefs.edit().putString("app_language",it.name).apply()},alpha,{alpha=it;prefs.edit().putFloat("glass_alpha",it).apply()},spacing,{spacing=it;prefs.edit().putFloat("nav_spacing",it).apply()},navBlur,{navBlur=it;prefs.edit().putFloat("nav_blur",it).apply()},navWidth,{navWidth=it;prefs.edit().putFloat("nav_width",it).apply()},arabic)
                }
            }
            AppNavigationBar(destination,{if(destination==Destination.SPACE)unlocked.value=false;destination=it},navStyle==NavigationStyle.FLOATING,alpha,spacing,navBlur,navWidth,Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),arabic)
        }}}
    }
}
private inline fun <reified T:Enum<T>> enumPref(v:String?,fallback:T):T=runCatching{enumValueOf<T>(v?:fallback.name)}.getOrDefault(fallback)
private fun destLabel(d:Destination,a:Boolean)=when(d){Destination.HOME->if(a)"الرئيسية" else "Home";Destination.BROWSE->if(a)"استعراض" else "Browse";Destination.SPACE->if(a)"المساحة الخاصة" else "Space";Destination.TRASH->if(a)"المحذوفات" else "Trash";Destination.SETTINGS->if(a)"الإعدادات" else "Settings"}
private fun fmt(n:Long)=when{n<1024->"$n B";n<1048576->"${n/1024} KB";else->String.format(Locale.getDefault(),"%.1f MB",n/1048576f)}
private fun copyRec(src:File,dst:File){if(src.isDirectory){if(!dst.exists())dst.mkdirs();src.listFiles()?.forEach{copyRec(it,File(dst,it.name))}}else{dst.parentFile?.mkdirs();src.copyTo(dst,true)}}
private fun zipFolder(source:File,out:File){ZipOutputStream(FileOutputStream(out)).use{z->fun add(f:File,base:String){if(f.isDirectory)f.listFiles()?.forEach{add(it,base+it.name+"/")}else{z.putNextEntry(ZipEntry(base+f.name));FileInputStream(f).use{it.copyTo(z)};z.closeEntry()}};if(source.isDirectory)source.listFiles()?.forEach{add(it,"")}else add(source,"")}}
private fun unzip(zip:File,dest:File){dest.mkdirs();ZipInputStream(FileInputStream(zip)).use{z->var e=z.nextEntry;while(e!=null){val out=File(dest,e.name);if(!out.canonicalPath.startsWith(dest.canonicalPath+File.separator))throw SecurityException("Invalid archive path");if(e.isDirectory)out.mkdirs()else{out.parentFile?.mkdirs();FileOutputStream(out).use{z.copyTo(it)}};z.closeEntry();e=z.nextEntry}}}
private fun openFile(context:Context,file:File){val uri=FileProvider.getUriForFile(context,context.packageName+".provider",file);val apk=file.extension.equals("apk",true);val i=Intent(if(apk)Intent.ACTION_INSTALL_PACKAGE else Intent.ACTION_VIEW).setDataAndType(uri,if(apk)"application/vnd.android.package-archive" else mime(file)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{context.startActivity(i)}catch(_:Exception){android.widget.Toast.makeText(context,"No compatible app found",android.widget.Toast.LENGTH_SHORT).show()}}
private fun mime(f:File)=when(f.extension.lowercase()){ "pdf"->"application/pdf";"jpg","jpeg"->"image/jpeg";"png"->"image/png";"mp4"->"video/mp4";"mp3"->"audio/mpeg";"txt"->"text/plain";"zip"->"application/zip";else->"*/*"}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun FileBrowser(context:Context,access:Boolean,request:()->Unit,refresh:Int,onRefresh:()->Unit,arabic:Boolean,home:Boolean){
    val external=remember{context.getExternalFilesDirs(null).getOrNull(1)?.let{var f:File?=it;repeat(4){f=f?.parentFile};f}?.takeIf{it.exists()}}
    var current by remember{mutableStateOf<File?>(null)}
    var collection by remember{mutableStateOf<String?>(null)}
    var playing by remember{mutableStateOf<File?>(null)}
    var selected by remember{mutableStateOf<File?>(null)}
    var action by remember{mutableStateOf<String?>(null)}
    var input by remember{mutableStateOf("")}
    var message by remember{mutableStateOf<String?>(null)}
    val space=remember{File(context.filesDir,"space").apply{mkdirs()}}
    BackHandler(enabled=current!=null||collection!=null){if(collection!=null)collection=null else current=current?.parentFile}
    val rootList=remember(refresh,current,access,home,collection){if(access){if(collection!=null){val found=queryMediaFiles(context,collection!!);if(collection=="downloads")found else found.sortedBy{it.name.lowercase()}} else {val base=current; if(base!=null)runCatching{base.listFiles()?.toList().orEmpty().sortedWith(compareBy<File>{!it.isDirectory}.thenBy{it.name.lowercase()})}.getOrDefault(emptyList()) else emptyList()}}else emptyList()}
    fun done(){selected=null;action=null;input="";onRefresh()}
    fun perform(a:String,f:File){
        try{when(a){
            "Delete","حذف"->{moveToTrash(context,f);message=if(arabic)"تم نقل الملف إلى سلة المحذوفات" else "Moved to Trash"}
            "Permanent delete","حذف نهائي"->{f.deleteRecursively()}
            "Rename","إعادة تسمية"->{val target=File(f.parentFile,input.trim());if(input.isNotBlank()&&f.renameTo(target)){}else throw Exception("Rename failed")}
            "Copy","نسخ","Move","نقل"->{val target=File(input.trim());if(input.isBlank())throw Exception("Enter destination path");copyRec(f,File(target,f.name));if(a=="Move"||a=="نقل")f.deleteRecursively()}
            "Compress","ضغط"->{val out=File(f.parentFile,f.nameWithoutExtension+".zip");zipFolder(f,out)}
            "Extract","استخراج"->{if(f.extension.lowercase()!="zip")throw Exception("ZIP extraction supported currently");unzip(f,File(f.parentFile,f.nameWithoutExtension))}
            "Move to Space","نقل إلى Space"->{copyRec(f,File(space,f.name));f.deleteRecursively()}
            "Share","مشاركة"->{val u=FileProvider.getUriForFile(context,context.packageName+".provider",f);val i=Intent(Intent.ACTION_SEND).setType(mime(f)).putExtra(Intent.EXTRA_STREAM,u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);context.startActivity(Intent.createChooser(i,"Share"))}
        }}catch(e:Exception){message=e.message?: "Operation failed"};done()
    }
    if(!access){Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.fillMaxWidth().padding(24.dp)){Icon(Icons.Default.FolderOpen,null,Modifier.size(54.dp));Text(if(arabic)"اسمح بالوصول إلى كل الملفات" else "Allow access to all files");Button(onClick=request){Text(if(arabic)"منح الصلاحية" else "Grant access")}}}
    else if(home&&current==null&&collection==null){
        val cards=listOf(Triple("Root","/",Icons.Default.AdminPanelSettings),Triple(if(arabic)"Internal Storage" else "Internal Storage",Environment.getExternalStorageDirectory().absolutePath,Icons.Default.PhoneAndroid),Triple(if(arabic)"External Storage" else "External Storage",external?.absolutePath?: "Not available",Icons.Default.SdCard))
        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=110.dp)){item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){Text(if(arabic)"مكتبتك" else "Your library",fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(Triple("videos",Icons.Default.VideoLibrary,if(arabic)"ملفات فيديو" else "Videos"),Triple("images",Icons.Default.PhotoLibrary,if(arabic)"الصور" else "Images"),Triple("downloads",Icons.Default.Download,if(arabic)"تحميلات" else "Downloads")).forEach{(key,ic,label)->Column(Modifier.weight(1f).combinedClickable(onClick={collection=key},onLongClick={}).padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(ic,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(28.dp));Text(label,style=MaterialTheme.typography.labelMedium)}}}}}};items(cards){(name,path,ic)->Card(Modifier.fillMaxWidth().combinedClickable(onClick={if(path=="Not available")message=if(arabic)"لا توجد بطاقة خارجية" else "No external SD card detected" else current=File(path)},onLongClick={message=path})){Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Icon(ic,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(16.dp));Column{Text(name,fontWeight=FontWeight.SemiBold);Text(path,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}}}
    }else{
        Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick={if(collection!=null)collection=null else current=current?.parentFile}){Icon(Icons.Default.ArrowBack,null)};Text(current?.absolutePath?:Environment.getExternalStorageDirectory().absolutePath,Modifier.weight(1f),maxLines=1);IconButton(onClick=onRefresh){Icon(Icons.Default.Refresh,null)}}
        LazyColumn(contentPadding=PaddingValues(bottom=110.dp)){items(rootList,key={it.absolutePath}){f->Row(Modifier.fillMaxWidth().combinedClickable(onClick={if(f.isDirectory)current=f else if(f.extension.equals("zip",true)){selected=f;action="Extract"}else if(isMedia(f))playing=f else openFile(context,f)},onLongClick={selected=f;action="menu"}).padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){Icon(if(f.isDirectory)Icons.Default.Folder else Icons.Default.InsertDriveFile,null,tint=if(f.isDirectory)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(f.name,maxLines=1);Text(if(f.isDirectory)"Folder" else fmt(f.length()),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};IconButton(onClick={selected=f;action="menu"}){Icon(Icons.Default.MoreVert,null)}};HorizontalDivider()}}
    }
    if(action=="menu"&&selected!=null){val f=selected!!;AlertDialog(onDismissRequest={action=null;selected=null},title={Text(f.name,maxLines=1)},text={Column{val opts=listOf("Open","Copy","Move","Delete","Share","Extract","Compress","Rename","Move to Space","Permanent delete");opts.forEach{opt->TextButton(onClick={if(opt=="Open"){action=null;selected=null;if(f.isDirectory)current=f else openFile(context,f)}else{action=opt;input=if(opt=="Rename")f.name else if(opt=="Copy"||opt=="Move")f.parentFile?.absolutePath?:"" else ""}} ,modifier=Modifier.fillMaxWidth()){Text(when(opt){"Open"->if(arabic)"فتح" else opt;"Copy"->if(arabic)"نسخ" else opt;"Move"->if(arabic)"نقل" else opt;"Delete"->if(arabic)"حذف" else opt;"Share"->if(arabic)"مشاركة" else opt;"Extract"->if(arabic)"فك الضغط / استخراج" else opt;"Compress"->if(arabic)"ضغط" else opt;"Rename"->if(arabic)"إعادة تسمية" else opt;"Move to Space"->if(arabic)"نقل إلى Space" else opt;else->if(arabic)"حذف نهائي" else opt})}}}},confirmButton={TextButton(onClick={action=null;selected=null}){Text(if(arabic)"إغلاق" else "Close")}})}
    if(action in listOf("Copy","Move","Rename")&&selected!=null){AlertDialog(onDismissRequest={action=null},title={Text(action!!)},text={OutlinedTextField(input,{input=it},label={Text(if(action=="Rename")"New name" else "Destination folder path")},singleLine=true)},confirmButton={TextButton(onClick={perform(action!!,selected!!)} ){Text("OK")}},dismissButton={TextButton(onClick={action=null;selected=null}){Text("Cancel")}})}
    if(action in listOf("Delete","حذف","Permanent delete","حذف نهائي","Compress","ضغط","Extract","استخراج","Move to Space","نقل إلى Space","Share","مشاركة")&&selected!=null){val f=selected!!;AlertDialog(onDismissRequest={action=null;selected=null},title={Text(action!!)},text={Text(if(arabic)"هل تريد تنفيذ العملية على ${f.name}؟" else "Perform this action on ${f.name}?")},confirmButton={TextButton(onClick={perform(action!!,f)}){Text(if(arabic)"تنفيذ" else "Confirm")}},dismissButton={TextButton(onClick={action=null;selected=null}){Text(if(arabic)"إلغاء" else "Cancel")}})}
    playing?.let{PlayerDialog(it){playing=null}}
    message?.let{AlertDialog(onDismissRequest={message=null},text={Text(it)},confirmButton={TextButton(onClick={message=null}){Text("OK")}})}
}


private fun trashPrefs(context:Context)=context.getSharedPreferences("google_file_plus_trash",Context.MODE_PRIVATE)
private fun trashDir(context:Context)=File(context.filesDir,"trash").apply{mkdirs()}
private fun moveToTrash(context:Context,source:File){
    val id=java.util.UUID.randomUUID().toString()
    val target=File(trashDir(context),id+"_"+source.name)
    copyRec(source,target)
    if(!source.deleteRecursively()){target.deleteRecursively();throw IllegalStateException("Could not remove original file")}
    trashPrefs(context).edit().putString(id,source.absolutePath+"|"+System.currentTimeMillis()+"|"+target.name).apply()
}
private fun cleanupTrash(context:Context){
    val prefs=trashPrefs(context);val now=System.currentTimeMillis();val edit=prefs.edit()
    prefs.all.forEach{(id,value)->val parts=(value as? String)?.split("|",limit=3)?:return@forEach
        val time=parts.getOrNull(1)?.toLongOrNull()?:0L
        if(now-time>=90L*24*60*60*1000){File(trashDir(context),parts.getOrNull(2)?: "").deleteRecursively();edit.remove(id)}
    };edit.apply()
}
@Composable private fun TrashScreen(context:Context,refresh:Int,onRefresh:()->Unit,arabic:Boolean){
    val prefs=trashPrefs(context);val entries=remember(refresh){prefs.all.mapNotNull{(id,v)->val p=(v as? String)?.split("|",limit=3)?:return@mapNotNull null;if(p.size<3)null else Triple(id,p[0],File(trashDir(context),p[2]))}}
    Column(Modifier.fillMaxSize().padding(bottom=100.dp)){
        Text(if(arabic)"تُحذف العناصر تلقائيًا بعد 90 يومًا" else "Items are automatically deleted after 90 days",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(entries.isEmpty())Text(if(arabic)"سلة المحذوفات فارغة" else "Trash is empty",Modifier.padding(20.dp))
        LazyColumn { items(entries,key={it.first}){(id,original,file)->
            Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Default.Delete,null);Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text(file.name,maxLines=1);Text(original,maxLines=1,style=MaterialTheme.typography.bodySmall)}
                IconButton(onClick={runCatching{val dest=File(original);dest.parentFile?.mkdirs();copyRec(file,dest);file.deleteRecursively();prefs.edit().remove(id).apply();onRefresh()}}){Icon(Icons.Default.Restore,null)}
                IconButton(onClick={file.deleteRecursively();prefs.edit().remove(id).apply();onRefresh()}){Icon(Icons.Default.DeleteForever,null)}
            };HorizontalDivider()
        }}
    }
}


private fun isMedia(f:File)=f.extension.lowercase() in setOf("mp4","mkv","webm","mov","avi","mp3","m4a","aac","flac","ogg","wav")
private fun queryMediaFiles(context:Context,kind:String):List<File>{
    if(kind=="downloads")return File(Environment.getExternalStorageDirectory(),Environment.DIRECTORY_DOWNLOADS).listFiles()?.toList().orEmpty().sortedByDescending{it.lastModified()}
    val uris=when(kind){ "videos"->listOf(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,MediaStore.Audio.Media.EXTERNAL_CONTENT_URI); "images"->listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI);else->emptyList() }
    val result=mutableListOf<File>()
    uris.forEach{uri->runCatching{context.contentResolver.query(uri,arrayOf(MediaStore.MediaColumns.DATA),null,null,MediaStore.MediaColumns.DATE_ADDED+" DESC")?.use{c->val idx=c.getColumnIndex(MediaStore.MediaColumns.DATA);while(c.moveToNext()){val path=c.getString(idx);if(!path.isNullOrBlank()){val f=File(path);if(f.exists())result.add(f)}}}}}
    return result.distinctBy{it.absolutePath}
}
@Composable private fun PlayerDialog(file:File,onClose:()->Unit){
    val context= LocalContext.current
    val player= remember(file){ExoPlayer.Builder(context).build().apply{setMediaItem(MediaItem.fromUri(Uri.fromFile(file)));prepare();playWhenReady=true}}
    DisposableEffect(player){onDispose{player.release()}}
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxWidth(.94f).heightIn(min=260.dp,max=560.dp),shape=RoundedCornerShape(20.dp),color=Color.Black){
            Column { Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){Text(file.name,Modifier.weight(1f),color=Color.White,maxLines=1);IconButton(onClick=onClose){Icon(Icons.Default.Close,null,tint=Color.White)}};AndroidView(factory={ctx->PlayerView(ctx).apply{this.player=player;useController=true;controllerAutoShow=true}},modifier=Modifier.fillMaxWidth().weight(1f)) }
        }
    }
}

@Composable private fun SpaceScreen(context:Context,prefs:android.content.SharedPreferences,unlocked:Boolean,setUnlocked:(Boolean)->Unit,refresh:()->Unit,arabic:Boolean){
    var password by remember{mutableStateOf("")};var confirm by remember{mutableStateOf("")};var error by remember{mutableStateOf("")}
    val hash=prefs.getString("space_password_hash",null)
    val dir=remember{File(context.filesDir,"space").apply{mkdirs()}}
    if(!unlocked){AlertDialog(onDismissRequest={},title={Text(if(hash==null)if(arabic)"إنشاء كلمة مرور Space" else "Create Space password" else if(arabic)"فتح Space" else "Unlock Space")},text={Column{OutlinedTextField(password,{password=it},label={Text(if(arabic)"كلمة المرور" else "Password")});if(hash==null)OutlinedTextField(confirm,{confirm=it},label={Text(if(arabic)"تأكيد كلمة المرور" else "Confirm password")});if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error)}},confirmButton={TextButton(onClick={if(password.length<4)error=if(arabic)"استخدم 4 أحرف على الأقل" else "Use at least 4 characters" else if(hash==null&&password!=confirm)error=if(arabic)"كلمتا المرور غير متطابقتين" else "Passwords do not match" else{val digest=sha(password);if(hash==null){prefs.edit().putString("space_password_hash",digest).apply();setUnlocked(true)}else if(digest==hash)setUnlocked(true)else error=if(arabic)"كلمة المرور غير صحيحة" else "Incorrect password"}}){Text(if(arabic)"متابعة" else "Continue")}})}
    else{Text(if(arabic)"الملفات الخاصة" else "Private files",fontWeight=FontWeight.Bold);Text(if(arabic)"يتم حفظ الملفات داخل مساحة التطبيق الخاصة." else "Files are stored in the app-private folder.");val files=dir.listFiles()?.toList().orEmpty();LazyColumn(contentPadding=PaddingValues(bottom=110.dp)){items(files){Text(it.name,Modifier.fillMaxWidth().padding(12.dp))}};TextButton(onClick={setUnlocked(false)}){Text(if(arabic)"قفل Space" else "Lock Space")}}
}
private fun sha(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}

@Composable private fun SettingsScreen(nav:NavigationStyle,onNav:(NavigationStyle)->Unit,theme:ThemeChoice,onTheme:(ThemeChoice)->Unit,language:AppLanguage,onLanguage:(AppLanguage)->Unit,alpha:Float,onAlpha:(Float)->Unit,spacing:Float,onSpacing:(Float)->Unit,navBlur:Float,onNavBlur:(Float)->Unit,navWidth:Float,onNavWidth:(Float)->Unit,arabic:Boolean){
    Column(Modifier.fillMaxWidth().padding(bottom=110.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text(if(arabic)"المظهر" else "Appearance",fontWeight=FontWeight.Bold)
        listOf(ThemeChoice.SYSTEM,ThemeChoice.LIGHT,ThemeChoice.DARK).forEach{Choice(if(arabic)when(it){ThemeChoice.SYSTEM->"حسب الجهاز";ThemeChoice.LIGHT->"فاتح";else->"داكن"}else when(it){ThemeChoice.SYSTEM->"System";ThemeChoice.LIGHT->"Light";else->"Dark"},theme==it){onTheme(it)}}
        Text(if(arabic)"اللغة" else "Language",fontWeight=FontWeight.Bold);listOf(AppLanguage.SYSTEM,AppLanguage.ARABIC,AppLanguage.ENGLISH).forEach{Choice(when(it){AppLanguage.SYSTEM->if(arabic)"لغة الجهاز" else "System";AppLanguage.ARABIC->"العربية";else->"English"},language==it){onLanguage(it)}}
        Text(if(arabic)"Navbar" else "Navigation bar",fontWeight=FontWeight.Bold);Choice(if(arabic)"عائم" else "Floating",nav==NavigationStyle.FLOATING){onNav(NavigationStyle.FLOATING)};Choice(if(arabic)"قياسي" else "Standard",nav==NavigationStyle.STANDARD){onNav(NavigationStyle.STANDARD)}
        Text(if(arabic)"شفافية خلفية شريط التنقل" else "Navigation bar transparency");Text("${(alpha*100).toInt()}%");Slider(alpha,onAlpha,valueRange=.15f..1f)
        Text(if(arabic)"قوة تأثير Blur" else "Blur strength");Text("${navBlur.toInt()} dp");Slider(navBlur,onNavBlur,valueRange=0f..24f)
        Text(if(arabic)"عرض الشريط العائم" else "Floating bar width");Text("${navWidth.toInt()}%");Slider(navWidth,onNavWidth,valueRange=55f..100f)
        Text(if(arabic)"المسافة بين أيقونات Navbar" else "Navbar icon spacing");Text("${spacing.toInt()} dp");Slider(spacing,onSpacing,valueRange=0f..20f)
    }
}
@Composable private fun Choice(label:String,selected:Boolean,onClick:()->Unit){Surface(onClick=onClick,shape=RoundedCornerShape(10.dp),color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.35f)){Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f));RadioButton(selected,onClick)}}}
@Composable private fun AppNavigationBar(selected:Destination,onSelect:(Destination)->Unit,floating:Boolean,alpha:Float,spacing:Float,blur:Float,width:Float,modifier:Modifier,arabic:Boolean){
    val items=listOf(Triple(Destination.HOME,Icons.Default.Home,if(arabic)"الرئيسية" else "Home"),Triple(Destination.SPACE,Icons.Default.Lock,"Space"),Triple(Destination.TRASH,Icons.Default.Delete,if(arabic)"المحذوفات" else "Trash"),Triple(Destination.SETTINGS,Icons.Default.Settings,if(arabic)"الإعدادات" else "Settings"))
    Surface(modifier.fillMaxWidth(if(floating) width/100f else 1f).then(if(floating)Modifier.padding(bottom=8.dp)else Modifier).height(64.dp).then(if(floating)Modifier.shadow(6.dp,RoundedCornerShape(24.dp))else Modifier).blur(blur.dp),shape=if(floating)RoundedCornerShape(24.dp)else RoundedCornerShape(0.dp),color=MaterialTheme.colorScheme.surface.copy(alpha=alpha),tonalElevation=2.dp){Row(Modifier.fillMaxSize().padding(horizontal=spacing.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){items.forEach{(d,ic,label)->NavigationBarItem(selected==d,{onSelect(d)},{Icon(ic,label,Modifier.size(19.dp))},label={Text(label,fontSize=9.sp,maxLines=1)},alwaysShowLabel=true)}}}
}
