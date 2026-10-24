import br.com.luma.camera.DeltaPatch;
import java.io.File;
public class DeltaCheck {
 public static void main(String[] args)throws Exception{DeltaPatch.apply(new File(args[0]),new File(args[1]),new File(args[2]));System.out.println(DeltaPatch.sha256(new File(args[2])));}
}
