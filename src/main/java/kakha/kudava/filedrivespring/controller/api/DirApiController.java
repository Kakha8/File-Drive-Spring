package kakha.kudava.filedrivespring.controller.api;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
public class DirApiController {

    @GetMapping("/api/dirs")
    public Object dirs(HttpSession session,
                       @RequestParam(required = false, defaultValue = "") String selectedDir) {

        String user = (String) session.getAttribute("USERNAME");

        selectedDir = selectedDir == null ? "" :
                selectedDir.replace("\\", "/").replaceAll("^/+", "").replaceAll("/+$", "");

        Path base = Paths.get("sftp-root/" + user).toAbsolutePath().normalize();   // root = uploads/
        Path resolved = selectedDir.isBlank() ? base : base.resolve(selectedDir).normalize();

        if (!resolved.startsWith(base)) {
            return Map.of("error", "invalid path");
        }

        File dir = resolved.toFile();
        if (!dir.isDirectory()) {
            return Map.of("error", "no such directory");
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return Map.of("error", "unable to list directory");
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (File f : files) {
            boolean isDir = f.isDirectory();
            String relPath = base.relativize(f.toPath().toAbsolutePath().normalize())
                    .toString().replace("\\", "/");
            result.add(Map.of(
                    "name", f.getName(),
                    "type", isDir ? "folder" : "file",
                    "path", relPath,
                    "size", f.isDirectory() ? 0L : f.length(),
                    "lastModified", f.lastModified()
            ));
        }

        return result;
    }


}
