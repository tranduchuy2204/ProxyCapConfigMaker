package vn.lapro;

import org.apache.commons.cli.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.io.*;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.Set;

public class Main {

    public static void main(String[] args) {
        CommandLineParser parser = new DefaultParser();
        Options options = new Options();


        options.addOption("file", true, "Đường dẫn tới tệp proxy");
        options.addOption("dir", true, "Dir");

        Properties properties = System.getProperties();
        Set<String> propertyNames = properties.stringPropertyNames();

        for (String propertyName : propertyNames) {
            String propertyValue = properties.getProperty(propertyName);
            System.out.println(propertyName + ": " + propertyValue);
        }

        try {
            CommandLine cmd = parser.parse(options, args);
            if (!cmd.hasOption("file")) {
                System.out.println("Vui lòng cung cấp đường dẫn đến tệp proxy bằng tùy chọn -file.");
                return;
            }
            if (!cmd.hasOption("dir")) {
                System.out.println("Vui lòng cung cấp -dir");
                return;
            }

            String filePath = cmd.getOptionValue("file");
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();
            Element rootElement = doc.createElement("proxycap_ruleset");
            rootElement.setAttribute("version", "537");

            doc.appendChild(rootElement);
            Element proxyServers = doc.createElement("proxy_servers");
            rootElement.appendChild(proxyServers);

            readProxyInfoFromFile(filePath, proxyServers, doc);

            Element routingRules = doc.createElement("routing_rules");
            rootElement.appendChild(routingRules);

            for (int i = 0; i < proxyServers.getChildNodes().getLength(); i++) {
                Element proxyEle = (Element) proxyServers.getChildNodes().item(i);
                String proxyName = proxyEle.getAttribute("name");
                String[] proxyNameSplit = proxyName.split("_");
                String dir =
                        cmd.getOptionValue("dir") + "\\system\\" + proxyNameSplit[1] + "\\jre_" + proxyNameSplit[2];
                Element routingRule = createRoutingRuleElement(doc, new String[]{
                        proxyName.replace("proxy", "rule"),
                        proxyEle.getAttribute("name"),
                        dir + "\\bin\\javaw.exe"
                });
                routingRules.appendChild(routingRule);

                String javaHome = System.getProperty("java.home");
                File sourceDirectory = new File(javaHome);
                File targetDirectory = new File(dir);
                try {
                    if (!targetDirectory.exists()) {
                        FileUtils.copyDirectory(sourceDirectory, targetDirectory);
                        System.out.println("Copy " + javaHome + " to " + dir);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            String output = "input.xml";
            File inputFile = new File(output);
            if (inputFile.exists()) {

                String backupFileName = "input.bak";
                File backupFile = new File(backupFileName);

                try {
                    Files.move(inputFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("Renamed from " + output + " to " + backupFileName);
                } catch (IOException e) {
                    System.err.println("Can not rename file: " + e.getMessage());
                }
            }
            StreamResult result = new StreamResult(new File(output));

            transformer.transform(source, result);
            command("xml2prs.exe " + output + " output.prs");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void readProxyInfoFromFile(String filePath, Element proxyServers, Document doc) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            int proxyCount = 1;
            int indexProxyDir = 0;
            while ((line = reader.readLine()) != null) {
                if (line.equals("[PREFIX]")) {
                    indexProxyDir++;
                    proxyCount = 1;
                    continue;
                }
                String[] proxyInfo = line.split(":");
                Element proxyServer = createProxyServerElement(doc, proxyInfo, proxyCount, indexProxyDir);
                proxyServers.appendChild(proxyServer);
                proxyCount++;
            }
            ((Element) proxyServers.getFirstChild()).setAttribute("is_default", "true");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Element createProxyServerElement(Document doc, String[] proxyInfo, int count, int indexProxyDir) {
        Element proxyServer = doc.createElement("proxy_server");
        proxyServer.setAttribute("name", "proxy_" + indexProxyDir + "_" + count);
        proxyServer.setAttribute("type", proxyInfo[0]);
        proxyServer.setAttribute("hostname", proxyInfo[1]);
        proxyServer.setAttribute("port", proxyInfo[2]);
        proxyServer.setAttribute("auth_method", "password");
        proxyServer.setAttribute("username", proxyInfo[3]);
        proxyServer.setAttribute("password", proxyInfo[4]);
        proxyServer.setAttribute("is_default", "false");
        return proxyServer;
    }

    private static Element createRoutingRuleElement(Document doc, String[] ruleInfo) {
        Element routingRule = doc.createElement("routing_rule");
        routingRule.setAttribute("name", ruleInfo[0]);
        routingRule.setAttribute("action", "proxy");
        routingRule.setAttribute("remote_dns", "false");
        routingRule.setAttribute("transports", "tcp");
        routingRule.setAttribute("disabled", "false");

        Element proxyOrChain = doc.createElement("proxy_or_chain");
        proxyOrChain.setAttribute("name", ruleInfo[1]);
        routingRule.appendChild(proxyOrChain);

        Element programs = doc.createElement("programs");
        Element program = doc.createElement("program");
        program.setAttribute("path", ruleInfo[2]);
        program.setAttribute("dir_included", "true");
        programs.appendChild(program);
        routingRule.appendChild(programs);

        return routingRule;
    }

    public static void command(String cmd) throws IOException, InterruptedException {
        System.out.println(cmd);
        Process process = Runtime.getRuntime().exec(cmd);

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }

        BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        while ((line = errorReader.readLine()) != null) {
            System.err.println(line);
        }

        int exitCode = process.waitFor();
        System.out.println("Exit Code: " + exitCode);
    }
}