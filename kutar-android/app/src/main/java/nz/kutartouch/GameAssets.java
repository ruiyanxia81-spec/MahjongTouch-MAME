package nz.kutartouch;
import android.app.Activity;
import android.graphics.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
/** Decodes assets from the user's EXE; does not execute Windows code. */
final class GameAssets {
    final File directory; final Map<String,Bitmap> images=new HashMap<>();
    private GameAssets(File directory){this.directory=directory;}
    static GameAssets load(Activity a,String game)throws IOException{
        File dir=new File(a.getFilesDir(),"native-assets-v1/"+game);dir.mkdirs();File marker=new File(dir,"complete");
        if(!marker.isFile()){
            byte[] exe;try(ZipFile z=new ZipFile(LibraryActivity.gameFile(a,game));InputStream in=z.getInputStream(z.getEntry(game+".exe"))){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[65536];int read;while((read=in.read(buffer))!=-1)bytes.write(buffer,0,read);exe=bytes.toByteArray();}
            int start=game.equals("Chan")?0xa1818:game.equals("Endroll")?0xa3818:0x668f0;boolean old=game.equals("HipDance");
            if(!old&&!(exe[start]=='K'&&exe[start+1]=='P'&&exe[start+2]=='D'&&exe[start+3]=='X'))throw new IOException("资源格式不符");
            int count=u32(exe,start+4),pos=start+8;if(count<1||count>100)throw new IOException("资源数量异常");
            ArrayList<String> names=new ArrayList<>();ArrayList<Integer> sizes=new ArrayList<>();
            for(int n=0;n<count;n++){
                int size;String name;
                if(old){int len=u32(exe,pos+4);size=u32(exe,pos+8);pos+=12;if(len<1||len>100)throw new IOException("资源名称异常");name=new String(exe,pos,len,java.nio.charset.StandardCharsets.US_ASCII);pos+=len;}
                else{size=u32(exe,pos);pos+=4;StringBuilder s=new StringBuilder();while(pos<exe.length&&(exe[pos]&255)!=77){s.append((char)(((exe[pos++]&255)-77)&255));if(s.length()>100)throw new IOException("资源名称异常");}pos++;name=s.toString();}
                if(!name.matches("[a-zA-Z0-9_]+\\.(bmp|BMP|wav|WAV)")||size<1||size>8000000)throw new IOException("资源范围异常");names.add(name);sizes.add(size);
            }
            for(int n=0;n<count;n++){
                int size=sizes.get(n);if(pos+size>exe.length)throw new IOException("资源不完整");byte[] data=Arrays.copyOfRange(exe,pos,pos+size);pos+=size;
                if(!old)for(int j=0;j<data.length;j++)data[j]=(byte)((data[j]&255)-38);
                try(FileOutputStream out=new FileOutputStream(new File(dir,names.get(n).toLowerCase(Locale.ROOT)))){out.write(data);}
            }marker.createNewFile();
        }
        GameAssets assets=new GameAssets(dir);File[] files=dir.listFiles();if(files==null)throw new IOException("无法读取资源");
        for(File file:files){if(!file.getName().endsWith(".bmp"))continue;Bitmap raw=BitmapFactory.decodeFile(file.getPath());if(raw==null)throw new IOException("图片无法解码："+file.getName());
            if(!file.getName().equals("bg.bmp")&&!file.getName().equals("title.bmp")){
                int[] pixels=new int[raw.getWidth()*raw.getHeight()];raw.getPixels(pixels,0,raw.getWidth(),0,0,raw.getWidth(),raw.getHeight());for(int n=0;n<pixels.length;n++)if((pixels[n]&0xffffff)==0)pixels[n]=0;
                Bitmap transparent=Bitmap.createBitmap(pixels,raw.getWidth(),raw.getHeight(),Bitmap.Config.ARGB_8888);raw.recycle();raw=transparent;
            }assets.images.put(file.getName(),raw);
        }return assets;
    }
    private static int u32(byte[] b,int p)throws IOException{if(p<0||p+4>b.length)throw new IOException("资源索引异常");return(b[p]&255)|((b[p+1]&255)<<8)|((b[p+2]&255)<<16)|((b[p+3]&255)<<24);}
    Bitmap image(String name){return images.get(name+".bmp");}
    void close(){for(Bitmap b:images.values())b.recycle();images.clear();}
}
