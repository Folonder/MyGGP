import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Test {
    public static void main(String[] args) {
        {
            String date = new SimpleDateFormat("dd_MM_yyyy__hh_mm_ss").format(new Date());
            System.out.println(date);
        }

        {
            String str = "Hello " + null;
            System.out.println(str.substring(str.indexOf(" ")+1).length());
        }
        /*
        // String dotFilePath = "tournaments/" + tournamentName + "/" + this.getName() + "__" + this.roleName.toString() + "/step_" + i + ".dot";
        String dotFilePath = "tournaments/hello/my/dear/file.dot";
        String dotFileContent = "digraph {\n";

        File outputLogFile = new File(dotFilePath);
        try {
            outputLogFile.getParentFile().mkdirs();
            outputLogFile.createNewFile();
            FileWriter outputLogFileWriter = new FileWriter(outputLogFile, false);
            outputLogFileWriter.write(dotFileContent);
            outputLogFileWriter.flush();
            outputLogFileWriter.close();
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        */

    }
}
