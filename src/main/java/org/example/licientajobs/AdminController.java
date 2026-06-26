package org.example.licientajobs;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/applications")
public class AdminController {

    private final StudentService studentService;

    @Autowired
    public AdminController(StudentService studentService) {
        this.studentService = studentService;
    }

    @PostMapping("/{studentId}/{applicationId}/accept")
    public String acceptJobApplication(@PathVariable Long studentId, @PathVariable Long applicationId) {
        studentService.updateJobApplicationStatus(studentId, applicationId, "ACCEPTED");
        return "redirect:/students"; // Redirect back to the student list page
    }

    @PostMapping("/{studentId}/{applicationId}/reject")
    public String rejectJobApplication(@PathVariable Long studentId, @PathVariable Long applicationId) {
        studentService.updateJobApplicationStatus(studentId, applicationId, "REJECTED");
        return "redirect:/students"; // Redirect back to the student list page
    }
}
