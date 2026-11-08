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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.activity.compose.BackHandler
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); scheduleTrashCleanup(this); setContent { GoogleFilePlusApp() } }
}
private enum class Destination { HOME, BROWSE, TRASH, SETTINGS }
private enum class NavigationStyle { FLOATING, STANDARD }
private enum class ThemeChoice { SYSTEM, LIGHT, DARK }
private enum class AppLanguage { SYSTEM, ENGLISH, ARABIC }

@Composable private fun GoogleFilePlusApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("google_file_plus_settings", Context.MODE_PRIVATE) }
    var destination by remember { mutableStateOf(Destination.HOME) }
    val backStack = remember { mutableStateListOf<Destination>() }
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
        if (destination != Destination.HOME) { destination=if(backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex) else Destination.HOME }
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
                    Destination.TRASH->TrashScreen(context,refresh,{refresh++},arabic)
                    Destination.SETTINGS->SettingsScreen(navStyle,{navStyle=it;prefs.edit().putString("navigation_style",it.name).apply()},theme,{theme=it;prefs.edit().putString("theme_choice",it.name).apply()},language,{language=it;prefs.edit().putString("app_language",it.name).apply()},alpha,{alpha=it;prefs.edit().putFloat("glass_alpha",it).apply()},spacing,{spacing=it;prefs.edit().putFloat("nav_spacing",it).apply()},navBlur,{navBlur=it;prefs.edit().putFloat("nav_blur",it).apply()},navWidth,{navWidth=it;prefs.edit().putFloat("nav_width",it).apply()},arabic)
                }
            }
            AppNavigationBar(destination,{if(it==Destination.HOME)backStack.clear() else if(destination!=it)backStack.add(destination);destination=it},navStyle==NavigationStyle.FLOATING,alpha,spacing,navBlur,navWidth,Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),arabic)
        }}}
    }
}
private inline fun <reified T:Enum<T>> enumPref(v:String?,fallback:T):T=runCatching{enumValueOf<T>(v?:fallback.name)}.getOrDefault(fallback)
private fun destLabel(d:Destination,a:Boolean)=when(d){Destination.HOME->if(a)"الرئيسية" else "Home";Destination.BROWSE->if(a)"استعراض" else "Browse";Destination.TRASH->if(a)"المحذوفات" else "Trash";Destination.SETTINGS->if(a)"الإعدادات" else "Settings"}
private fun fmt(n:Long)=when{n<1024->"$n B";n<1048576->"${n/1024} KB";else->String.format(Locale.getDefault(),"%.1f MB",n/1048576f)}
private fun copyRec(src:File,dst:File){if(src.isDirectory){if(!dst.exists())dst.mkdirs();src.listFiles()?.forEach{copyRec(it,File(dst,it.name))}}else{dst.parentFile?.mkdirs();src.copyTo(dst,true)}}
private fun zipFolder(source:File,out:File){ZipOutputStream(FileOutputStream(out)).use{z->fun add(f:File,base:String){if(f.isDirectory)f.listFiles()?.forEach{add(it,base+it.name+"/")}else{z.putNextEntry(ZipEntry(base+f.name));FileInputStream(f).use{it.copyTo(z)};z.closeEntry()}};if(source.isDirectory)source.listFiles()?.forEach{add(it,"")}else add(source,"")}}
private fun unzip(zip:File,dest:File){dest.mkdirs();ZipInputStream(FileInputStream(zip)).use{z->var e=z.nextEntry;while(e!=null){val out=File(dest,e.name);if(!out.canonicalPath.startsWith(dest.canonicalPath+File.separator))throw SecurityException("Invalid archive path");if(e.isDirectory)out.mkdirs()else{out.parentFile?.mkdirs();FileOutputStream(out).use{z.copyTo(it)}};z.closeEntry();e=z.nextEntry}}}
private fun openFile(context:Context,file:File){val uri=FileProvider.getUriForFile(context,context.packageName+".provider",file);val apk=file.extension.equals("apk",true);val i=Intent(if(apk)Intent.ACTION_INSTALL_PACKAGE else Intent.ACTION_VIEW).setDataAndType(uri,if(apk)"application/vnd.android.package-archive" else mime(file)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{context.startActivity(i)}catch(_:Exception){android.widget.Toast.makeText(context,"No compatible app found",android.widget.Toast.LENGTH_SHORT).show()}}
private fun mime(f:File)=when(f.extension.lowercase()){ "pdf"->"application/pdf";"jpg","jpeg"->"image/jpeg";"png"->"image/png";"mp4"->"video/mp4";"mp3"->"audio/mpeg";"txt"->"text/plain";"zip"->"application/zip";else->"*/*"}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun FileBrowser(context:Context,access:Boolean,request:()->Unit,refresh:Int,onRefresh:()->Unit,arabic:Boolean,home:Boolean){
    val internal=remember{Environment.getExternalStorageDirectory()}
    val external=remember{context.getExternalFilesDirs(null).getOrNull(1)?.let{var f:File?=it;repeat(4){f=f?.parentFile};f}?.takeIf{it.exists()}}
    var current by remember{mutableStateOf<File?>(null)}
    var collection by remember{mutableStateOf<String?>(null)}
    var playing by remember{mutableStateOf<File?>(null)}
    var menuFile by remember{mutableStateOf<File?>(null)}
    var pending by remember{mutableStateOf<String?>(null)}
    var selected by remember{mutableStateOf(setOf<File>())}
    var searchOpen by remember{mutableStateOf(false)}
    var query by remember{mutableStateOf("")}
    var sort by remember{mutableStateOf("az")}
    var sortMenu by remember{mutableStateOf(false)}
    var grid by remember{mutableStateOf(false)}
    var folderDialog by remember{mutableStateOf(false)}
    var folderName by remember{mutableStateOf("")}
    var picker by remember{mutableStateOf(false)}
    var details by remember{mutableStateOf<File?>(null)}
    var message by remember{mutableStateOf<String?>(null)}
    val folderFocus=remember{FocusRequester()}
    val keyboard=LocalSoftwareKeyboardController.current
    LaunchedEffect(folderDialog){if(folderDialog){folderFocus.requestFocus();keyboard?.show()}}
    LaunchedEffect(home){if(!home&&current==null)current=internal}
    BackHandler(enabled=current!=null||collection!=null||selected.isNotEmpty()||searchOpen){
        when{selected.isNotEmpty()->selected=emptySet();searchOpen->{searchOpen=false;query=""};collection!=null->collection=null;else->current=current?.parentFile}
    }
    val files=remember(refresh,current,access,collection,sort,query){
        val all=if(!access) emptyList() else if(searchOpen&&query.isNotBlank()&&collection==null)searchAllFiles(context,query) else if(collection!=null)queryMediaFiles(context,collection!!) else current?.let{runCatching{it.listFiles()?.toList().orEmpty()}.getOrDefault(emptyList())}.orEmpty()
        val filtered=all.filter{it.name.contains(query,true)}
        when(sort){"newest"->filtered.sortedByDescending{it.lastModified()};"oldest"->filtered.sortedBy{it.lastModified()};"largest"->filtered.sortedByDescending{if(it.isFile)it.length() else 0L};else->filtered.sortedBy{it.name.lowercase(Locale.ROOT)}}
    }
    fun toggle(f:File){selected=if(f in selected)selected-f else selected+f}
    fun open(f:File){if(selected.isNotEmpty())toggle(f) else if(f.isDirectory)current=f else if(isMedia(f))playing=f else openFile(context,f)}
    fun create(){
        val parent=current?:internal
        val name=folderName.trim()
        if(name.isBlank()){message=if(arabic)"اكتب اسم المجلد" else "Enter a folder name";return}
        val target=File(parent,name)
        if(target.exists()){message=if(arabic)"الاسم مستخدم بالفعل" else "That name already exists";return}
        if(target.mkdir()){folderDialog=false;folderName="";onRefresh()}else message=if(arabic)"تعذر إنشاء المجلد" else "Could not create folder"
    }
    fun finishCopyMove(dest:File){
        val action=pending?:return
        val errors=mutableListOf<String>()
        selected.forEach{src->runCatching{
            val target=File(dest,src.name)
            if(src.canonicalPath==target.canonicalPath)throw Exception("Same folder")
            if(action=="Move"&&target.exists())throw Exception("Destination already exists")
            copyRec(src,target)
            if(action=="Move"&&!src.deleteRecursively()){target.deleteRecursively();throw Exception("Could not delete original")}
        }.onFailure{errors.add(src.name+": "+it.message)}}
        picker=false;pending=null;selected=emptySet();onRefresh()
        message=if(errors.isEmpty())if(arabic)"تمت العملية بنجاح" else "$action completed" else errors.joinToString("\n")
    }
    if(!access){
        Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.fillMaxWidth().padding(24.dp)){Icon(Icons.Default.FolderOpen,null,Modifier.size(54.dp));Text(if(arabic)"اسمح بالوصول إلى كل الملفات" else "Allow access to all files");Button(onClick=request){Text(if(arabic)"منح الصلاحية" else "Grant access")}}
    }else if(home&&current==null&&collection==null){
        val roots=listOf(Triple("Root","/",Icons.Default.AdminPanelSettings),Triple("Internal Storage",internal.absolutePath,Icons.Default.PhoneAndroid),Triple(if(arabic)"External Storage" else "External Storage",external?.absolutePath?:"Not available",Icons.Default.SdCard))
        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=110.dp)){
            item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){
                Text(if(arabic)"مكتبتك" else "Your library",fontWeight=FontWeight.Bold)
                Row(horizontalArrangement=Arrangement.spacedBy(2.dp)){
                    listOf(Triple("videos",Icons.Default.VideoLibrary,if(arabic)"فيديو" else "Videos"),Triple("audio",Icons.Default.AudioFile,if(arabic)"صوتيات" else "Audio"),Triple("images",Icons.Default.PhotoLibrary,if(arabic)"صور" else "Images"),Triple("downloads",Icons.Default.Download,if(arabic)"تحميل" else "Downloads")).forEach{(key,icon,label)->
                        Column(Modifier.weight(1f).combinedClickable(onClick={collection=key}).padding(6.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(26.dp));Text(label,style=MaterialTheme.typography.labelSmall,maxLines=1)}
                    }
                }
            }}}
            items(roots){(name,path,icon)->Card(Modifier.fillMaxWidth().combinedClickable(onClick={if(path=="Not available")message=if(arabic)"لا توجد بطاقة خارجية" else "No external SD card" else current=File(path)})){
                Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(12.dp));Column{Text(name,fontWeight=FontWeight.SemiBold);Text(path,style=MaterialTheme.typography.bodySmall)}}
            }}
        }
    }else{
        Row(verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick={if(selected.isNotEmpty())selected=emptySet() else if(collection!=null)collection=null else current=current?.parentFile}){Icon(Icons.Default.ArrowBack,null)}
            Text(if(selected.isNotEmpty())"${selected.size} selected" else collection?:current?.absolutePath?:internal.absolutePath,Modifier.weight(1f),maxLines=1,style=MaterialTheme.typography.bodySmall)
            IconButton(onClick={selected=if(selected.size==files.size&&files.isNotEmpty())emptySet() else files.toSet()}){Icon(Icons.Default.SelectAll,if(arabic)"تحديد الكل" else "Select all")}
            IconButton(onClick={searchOpen=!searchOpen;query=""}){Icon(Icons.Default.Search,if(arabic)"بحث" else "Search")}
            IconButton(onClick=onRefresh){Icon(Icons.Default.Refresh,if(arabic)"تحديث" else "Refresh")}
        }
        if(searchOpen)OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,placeholder={Text(if(arabic)"ابحث عن ملف" else "Search files")},trailingIcon={IconButton(onClick={query=""}){Icon(Icons.Default.Close,null)}})
        Row(verticalAlignment=Alignment.CenterVertically){
            if(collection==null)TextButton(onClick={folderDialog=true}){Icon(Icons.Default.CreateNewFolder,null);Text(if(arabic)"مجلد جديد" else "New Folder")}
            Box{TextButton(onClick={sortMenu=true}){Icon(Icons.Default.Sort,null);Text(if(arabic)"ترتيب" else "Sort")}
                DropdownMenu(sortMenu,{sortMenu=false}){
                    listOf("az" to "A–Z","newest" to (if(arabic)"الأحدث" else "Newest"),"oldest" to (if(arabic)"الأقدم" else "Oldest"),"largest" to (if(arabic)"الأكبر حجمًا" else "Largest")).forEach{(v,label)->DropdownMenuItem(text={Text(label)},onClick={sort=v;sortMenu=false})}
                }}
            Spacer(Modifier.weight(1f));IconButton(onClick={grid=!grid}){Icon(if(grid)Icons.Default.ViewList else Icons.Default.GridView,if(grid)"List" else "Grid")}
        }
        if(selected.isNotEmpty())Row{
            TextButton(onClick={pending="Copy";picker=true}){Icon(Icons.Default.ContentCopy,null);Text(if(arabic)"نسخ" else "Copy")}
            TextButton(onClick={pending="Move";picker=true}){Icon(Icons.Default.DriveFileMove,null);Text(if(arabic)"نقل" else "Move")}
            TextButton(onClick={pending="Delete"}){Icon(Icons.Default.Delete,null);Text(if(arabic)"حذف" else "Delete")}
        }
        if(files.isEmpty())Text(if(arabic)"لا توجد ملفات" else "No files found",Modifier.padding(20.dp))
        else if(grid){
            LazyVerticalGrid(GridCells.Adaptive(112.dp),modifier=Modifier.fillMaxWidth().heightIn(max=560.dp),contentPadding=PaddingValues(bottom=110.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                gridItems(files,key={it.absolutePath}){f->Card(Modifier.fillMaxWidth().combinedClickable(onClick={open(f)},onLongClick={if(selected.isEmpty())menuFile=f else toggle(f)})){
                    Column(Modifier.padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally){
                        Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.TopEnd){Icon(fileIcon(f),null,Modifier.padding(top=10.dp).size(48.dp),tint=MaterialTheme.colorScheme.primary);Checkbox(f in selected,{toggle(f)},modifier=Modifier.size(28.dp))}
                        Text(f.name,maxLines=2,minLines=2,style=MaterialTheme.typography.bodySmall)
                        Text(if(f.isDirectory)"Folder" else fmt(f.length()),style=MaterialTheme.typography.labelSmall)
                        TextButton(onClick={details=f},contentPadding=PaddingValues(0.dp)){Text(if(arabic)"التفاصيل" else "Details")}
                    }
                }}
            }
        }else{
            LazyColumn(Modifier.fillMaxWidth().heightIn(max=560.dp),contentPadding=PaddingValues(bottom=110.dp)){
                items(files,key={it.absolutePath}){f->Row(Modifier.fillMaxWidth().combinedClickable(onClick={open(f)},onLongClick={if(selected.isEmpty())menuFile=f else toggle(f)}).padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically){
                    Icon(fileIcon(f),null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(f.name,maxLines=1);Text(if(f.isDirectory)"Folder" else fmt(f.length()),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    Checkbox(f in selected,{toggle(f)});IconButton(onClick={details=f}){Icon(Icons.Default.Info,if(arabic)"التفاصيل" else "Details")};IconButton(onClick={menuFile=f}){Icon(Icons.Default.MoreVert,null)}
                };HorizontalDivider()}
            }
        }
    }
    menuFile?.let{f->AlertDialog(onDismissRequest={menuFile=null},title={Text(f.name,maxLines=1)},text={Column{
        listOf("Open","Copy","Move","Details","Rename","Delete","Share","Compress","Extract").forEach{op->TextButton(onClick={menuFile=null;when(op){
            "Open"->open(f);"Copy","Move"->{selected=setOf(f);pending=op;picker=true};"Details"->details=f
            "Rename"->{selected=setOf(f);pending="Rename"};"Delete"->{selected=setOf(f);pending="Delete"}
            "Share"->runCatching{val u=FileProvider.getUriForFile(context,context.packageName+".provider",f);context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime(f)).putExtra(Intent.EXTRA_STREAM,u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),"Share"))}.onFailure{message=it.message}
            "Compress"->runCatching{zipFolder(f,File(f.parentFile,f.nameWithoutExtension+".zip"));onRefresh()}.onFailure{message=it.message}
            "Extract"->runCatching{unzip(f,File(f.parentFile,f.nameWithoutExtension));onRefresh()}.onFailure{message=it.message}
        }},modifier=Modifier.fillMaxWidth()){Text(when(op){"Details"->if(arabic)"بيانات الملف" else op;"Rename"->if(arabic)"إعادة تسمية" else op;"Delete"->if(arabic)"حذف" else op;else->op})}}
    }},confirmButton={TextButton(onClick={menuFile=null}){Text("Close")}})}
    if(pending=="Rename"&&selected.size==1){
        var newName by remember(selected){mutableStateOf(selected.first().name)}
        AlertDialog(onDismissRequest={pending=null;selected=emptySet()},title={Text(if(arabic)"إعادة تسمية" else "Rename")},text={OutlinedTextField(newName,{newName=it},singleLine=true)},confirmButton={TextButton(onClick={val f=selected.first();val ok=newName.isNotBlank()&&f.renameTo(File(f.parentFile,newName.trim()));pending=null;selected=emptySet();onRefresh();if(!ok)message="Rename failed"}){Text(if(arabic)"حفظ" else "Save")}},dismissButton={TextButton(onClick={pending=null;selected=emptySet()}){Text("Cancel")}})
    }
    if(pending=="Delete"&&selected.isNotEmpty())AlertDialog(onDismissRequest={pending=null;selected=emptySet()},title={Text(if(arabic)"حذف العناصر" else "Delete items")},text={Text(if(arabic)"نقل ${selected.size} عنصر إلى سلة المحذوفات؟" else "Move ${selected.size} item(s) to Trash?")},confirmButton={TextButton(onClick={val errors=selected.toList().mapNotNull{f->runCatching{moveToTrash(context,f)}.exceptionOrNull()?.message};pending=null;selected=emptySet();onRefresh();if(errors.isNotEmpty())message=errors.joinToString("\n")}){Text(if(arabic)"تأكيد" else "Confirm")}},dismissButton={TextButton(onClick={pending=null;selected=emptySet()}){Text("Cancel")}})
    if(folderDialog)AlertDialog(onDismissRequest={folderDialog=false},title={Text(if(arabic)"مجلد جديد" else "New Folder")},text={OutlinedTextField(folderName,{folderName=it},modifier=Modifier.focusRequester(folderFocus),singleLine=true,placeholder={Text(if(arabic)"اسم المجلد" else "Folder name")})},confirmButton={TextButton(onClick={create()}){Text(if(arabic)"إنشاء" else "Create")}},dismissButton={TextButton(onClick={folderDialog=false}){Text("Cancel")}})
    if(picker)DestinationPickerDialog(arabic,internal,external,{picker=false;pending=null;selected=emptySet()},{finishCopyMove(it)})
    details?.let{f->val date=SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault()).format(Date(f.lastModified()))
        AlertDialog(onDismissRequest={details=null},title={Text(if(arabic)"بيانات الملف" else "File details")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            DetailLine(if(arabic)"الاسم" else "Name",f.name);DetailLine(if(arabic)"الامتداد" else "Extension",if(f.isDirectory)"Folder" else f.extension);DetailLine(if(arabic)"الحجم" else "Size",if(f.isDirectory)"—" else fmt(f.length()));DetailLine(if(arabic)"تاريخ التعديل" else "Modified",date);DetailLine(if(arabic)"المسار" else "Path",f.absolutePath)
        }},confirmButton={TextButton(onClick={details=null}){Text("Close")}})
    }
    playing?.let{PlayerDialog(it){playing=null}}
    message?.let{AlertDialog(onDismissRequest={message=null},text={Text(it)},confirmButton={TextButton(onClick={message=null}){Text("OK")}})}
}
@Composable private fun DetailLine(label:String,value:String){Column{Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary);Text(value,style=MaterialTheme.typography.bodyMedium)}}
private fun fileIcon(f:File)=when{f.isDirectory->Icons.Default.Folder;isVideo(f)->Icons.Default.VideoFile;isAudio(f)->Icons.Default.AudioFile;f.extension.lowercase() in setOf("jpg","jpeg","png","gif","webp","bmp","heic")->Icons.Default.Image;else->Icons.Default.InsertDriveFile}

@Composable private fun DestinationPickerDialog(arabic:Boolean,internal:File,external:File?,onDismiss:()->Unit,onChoose:(File)->Unit){
    var current by remember{mutableStateOf<File?>(null)}
    val roots=remember(internal,external){listOf(File("/"),internal)+listOfNotNull(external).distinctBy{it.absolutePath}}
    val dirs=remember(current){current?.let{dir->runCatching{dir.listFiles()?.filter{child->child.isDirectory&&child.canRead()}?.sortedBy{child->child.name.lowercase(Locale.ROOT)}.orEmpty()}.getOrDefault(emptyList())}.orEmpty()}
    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text(if(arabic)"اختيار مكان النسخ أو النقل" else "Choose destination")},
        text={
            Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState())){
                Row(verticalAlignment=Alignment.CenterVertically){
                    TextButton(onClick={current=current?.parentFile}){Icon(Icons.Default.ArrowBack,null)}
                    Text(current?.absolutePath?:if(arabic)"اختر وحدة التخزين" else "Choose storage",Modifier.weight(1f),maxLines=1)
                    if(current!=null)TextButton(onClick={onChoose(current!!)} ){Text(if(arabic)"اختر هذا المجلد" else "Use folder")}
                }
                HorizontalDivider()
                if(current==null){
                    roots.forEach{root->TextButton(onClick={current=root},modifier=Modifier.fillMaxWidth()){
                        Icon(if(root.absolutePath=="/")Icons.Default.AdminPanelSettings else if(root==internal)Icons.Default.PhoneAndroid else Icons.Default.SdCard,null)
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment=Alignment.Start){Text(if(root.absolutePath=="/")"Root" else if(root==internal)"Internal Storage" else "External Storage");Text(root.absolutePath,style=MaterialTheme.typography.bodySmall)}
                    }}
                }else{
                    dirs.forEach{dir->TextButton(onClick={current=dir},modifier=Modifier.fillMaxWidth()){
                        Icon(Icons.Default.Folder,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(8.dp));Text(dir.name)
                    }}
                }
            }
        },
        confirmButton={TextButton(onClick=onDismiss){Text(if(arabic)"إلغاء" else "Cancel")}}
    )
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

private fun scheduleTrashCleanup(context:Context){
    val request=PeriodicWorkRequestBuilder<TrashCleanupWorker>(1,TimeUnit.DAYS).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("google_file_plus_trash_cleanup",ExistingPeriodicWorkPolicy.KEEP,request)
}
class TrashCleanupWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
    override suspend fun doWork():Result { cleanupTrash(applicationContext); return Result.success() }
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


private val VIDEO_EXTENSIONS=setOf("mp4","mkv","webm","mov","avi","m4v","3gp","mpeg","mpg","ts","wmv","flv","ogv")
private val AUDIO_EXTENSIONS=setOf("mp3","m4a","aac","flac","ogg","wav","wma","opus","amr","aiff","aif","mid","midi","ape")
private fun isVideo(f:File)=f.extension.lowercase(Locale.ROOT) in VIDEO_EXTENSIONS
private fun isAudio(f:File)=f.extension.lowercase(Locale.ROOT) in AUDIO_EXTENSIONS
private fun isMedia(f:File)=isVideo(f)||isAudio(f)
private fun searchAllFiles(context:Context,query:String):List<File>{
    val result=mutableListOf<File>()
    runCatching{
        context.contentResolver.query(MediaStore.Files.getContentUri("external"),arrayOf(MediaStore.MediaColumns.DATA),MediaStore.MediaColumns.DISPLAY_NAME+" LIKE ?",arrayOf("%"+query+"%"),MediaStore.MediaColumns.DATE_MODIFIED+" DESC")?.use{cursor->
            val index=cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            if(index>=0)while(cursor.moveToNext()){val path=cursor.getString(index);if(!path.isNullOrBlank()){val f=File(path);if(f.exists())result.add(f)}}
        }
    }
    return result.distinctBy{it.absolutePath}
}
private fun queryMediaFiles(context:Context,kind:String):List<File>{
    if(kind=="downloads")return File(Environment.getExternalStorageDirectory(),Environment.DIRECTORY_DOWNLOADS).listFiles()?.toList().orEmpty()
    val uri=when(kind){"videos"->MediaStore.Video.Media.EXTERNAL_CONTENT_URI;"audio"->MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;"images"->MediaStore.Images.Media.EXTERNAL_CONTENT_URI;else->return emptyList()}
    val result=mutableListOf<File>()
    runCatching{context.contentResolver.query(uri,arrayOf(MediaStore.MediaColumns.DATA),null,null,MediaStore.MediaColumns.DATE_ADDED+" DESC")?.use{cursor->
        val index=cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
        if(index>=0)while(cursor.moveToNext()){val path=cursor.getString(index);if(!path.isNullOrBlank()){val f=File(path);if(f.exists())result.add(f)}}
    }}
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
    val items=listOf(Triple(Destination.HOME,Icons.Default.Home,if(arabic)"الرئيسية" else "Home"),Triple(Destination.TRASH,Icons.Default.Delete,if(arabic)"المحذوفات" else "Trash"),Triple(Destination.SETTINGS,Icons.Default.Settings,if(arabic)"الإعدادات" else "Settings"))
    Box(modifier.fillMaxWidth(if(floating)width/100f else 1f).then(if(floating)Modifier.padding(bottom=8.dp)else Modifier).height(64.dp).then(if(floating)Modifier.shadow(6.dp,RoundedCornerShape(24.dp))else Modifier)){
        Surface(Modifier.matchParentSize().blur(blur.dp),shape=if(floating)RoundedCornerShape(24.dp)else RoundedCornerShape(0.dp),color=MaterialTheme.colorScheme.surface.copy(alpha=alpha),tonalElevation=2.dp){}
        Row(Modifier.fillMaxSize().padding(horizontal=spacing.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){
            items.forEach{(d,ic,label)->NavigationBarItem(selected==d,{onSelect(d)},{Icon(ic,label,Modifier.size(19.dp))},label={Text(label,fontSize=9.sp,maxLines=1)},alwaysShowLabel=true)}
        }
    }
}
