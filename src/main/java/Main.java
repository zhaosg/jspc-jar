import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class Main {
    public static Logger logger = LoggerFactory.getLogger(Main.class);
    private static Properties config = new Properties();
    private static String configUrl = "/application.properties";
    private static VelocityContext context = new VelocityContext();
    private static String parent;
    private static String tomcat_home;
    private static String app_dir;

    static {
        init();
    }

    public static void init() {
        parent = new File("").getAbsolutePath();

        loadConfig();
        Properties p = new Properties();
        p.setProperty(Velocity.INPUT_ENCODING, "UTF-8");
        p.setProperty(Velocity.OUTPUT_ENCODING, "UTF-8");
        p.setProperty("file.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        Velocity.init(p);
    }


    public static Template getTemplate(String templateFile) {
        Template template = null;
        try {
            template = Velocity.getTemplate(templateFile);
        } catch (Exception e) {
            logger.error("", e);
        }
        return template;
    }


    public static Properties loadConfig() {
        try {
            config.load(Main.class.getResourceAsStream(configUrl));
            tomcat_home = config.getProperty("tomcat.home");
            app_dir = config.getProperty("app.dir");
        } catch (Exception e) {
            logger.error("", e);
        }
        return config;
    }

    /**
     * 从网络Url中下载文件
     *
     * @param urlStr
     * @param fileName
     * @param savePath
     * @throws IOException
     */
    public static void downLoadFromUrl(String urlStr, String fileName, String savePath) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        //设置超时间为3秒
        conn.setConnectTimeout(3 * 1000);
        //防止屏蔽程序抓取而返回403错误
        conn.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");

        //得到输入流
        InputStream inputStream = conn.getInputStream();
        //获取自己数组
        byte[] getData = readInputStream(inputStream);

        //文件保存位置
        File saveDir = new File(savePath);
        if (!saveDir.exists()) {
            saveDir.mkdir();
        }
        File file = new File(saveDir + File.separator + fileName);
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(getData);
        if (fos != null) {
            fos.close();
        }
        if (inputStream != null) {
            inputStream.close();
        }
        System.out.println("info:" + url + " download success");
    }

    public static byte[] readInputStream(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[1024];
        int len = 0;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        while ((len = inputStream.read(buffer)) != -1) {
            bos.write(buffer, 0, len);
        }
        bos.close();
        return bos.toByteArray();
    }

    public static void generateWebFragment() {
        try {
            String webxml = parent + File.separator + "jsp-pages" + File.separator + "generated_web.xml";
            File webxmlfile = new File(webxml);
            String servlets = FileUtils.readFileToString(webxmlfile);
            context.put("servlets", servlets);
            StringWriter writer = new StringWriter();
            Template template = getTemplate("/templates/web.xml.vm");
            if (template != null)
                template.merge(context, writer);
            File file = new File(parent + File.separator + "jsp-pages" + File.separator + "META-INF" + File.separator + "web-fragment.xml");
            FileUtils.writeStringToFile(file, writer.toString());
            FileUtils.deleteQuietly(webxmlfile);
        } catch (Exception e) {
            logger.error("", e);
        }
    }

    public static void createJarArchive(File archiveFile, File tempJarDir) throws IOException {
        JarOutputStream jos = null;
        try {
            jos = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(archiveFile)), new Manifest());

            int pathLength = tempJarDir.getAbsolutePath().length() + 1;
            Collection<File> files = FileUtils.listFiles(tempJarDir, null, true);
            for (final File file : files) {
                if (!file.isFile()) {
                    continue;
                }
                String name = file.getAbsolutePath().substring(pathLength);
                name = name.replace('\\', '/');
                JarEntry jarFile = new JarEntry(name);
                jos.putNextEntry(jarFile);

                FileUtils.copyFile(file, jos);
            }
        } finally {
            IOUtils.closeQuietly(jos);
        }
    }

    public static void generateJar() {
        try {
            createJarArchive(new File(parent + File.separator + "jsp-pages.jar"), new File(parent + File.separator + "jsp-pages"));
            logger.info("jar servlets：success");
            FileUtils.deleteDirectory(new File(parent + File.separator + "jsp-pages"));
        } catch (Exception e) {
            logger.error("", e);
        }
    }

    public static void compile() {
        //创建一个ANT项目
        Project p = new Project();
        try {
            FileUtils.deleteDirectory(new File(parent + File.separator + "jsp-pages"));
            FileUtils.forceMkdir(new File(parent + File.separator + "jsp-pages"));
            String buildfile = new File(Thread.currentThread().getContextClassLoader().getResource("build.xml").getFile()).getAbsolutePath();
            File buildFile = new File(buildfile);
            //创建一个默认的监听器,监听项目构建过程中的日志操作
            DefaultLogger consoleLogger = new DefaultLogger();
            consoleLogger.setErrorPrintStream(System.err);
            consoleLogger.setOutputPrintStream(System.out);
            consoleLogger.setMessageOutputLevel(Project.MSG_INFO);

            p.addBuildListener(consoleLogger);
            p.fireBuildStarted();
            p.setProperty("tomcat.home", tomcat_home);
            p.setProperty("root.dir", parent);
            p.setProperty("app.dir", app_dir);
            String java_home = System.getProperty("java.home");
            if (java_home.endsWith("jre")) {
                java_home = java_home.substring(0, java_home.length() - 5);
            }
            p.setProperty("jdk.home", "C:\\Program Files\\Java\\jdk1.8.0_10");
            p.init();
            ProjectHelper.configureProject(p, buildFile);
            p.executeTarget(p.getDefaultTarget());
            p.fireBuildFinished(null);
        } catch (BuildException be) {
            p.fireBuildFinished(be);
        } catch (Exception e) {

        }
    }


    public static void main(String[] args) throws Exception {
        Main.compile();
        Main.generateWebFragment();
        Main.generateJar();
    }

}
