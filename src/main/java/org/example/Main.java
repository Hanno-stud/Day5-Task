package org.example;

import com.mongodb.client.*;
import com.mongodb.client.model.*;
import com.mongodb.client.result.DeleteResult;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.time.LocalDate;
import java.util.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Sorts.*;


class Employee {
    String ID, name, dept, email;
    int salary;
    List<String> skills;
    LocalDate joinDate;

    public void displayInfo() {
        System.out.printf("\nEmployee:\nID : %s\nName : %s\nDepartment : %s\nEmail : %s\nSalary : %d\nSkills: %s\nJoin Date: %s\n",
                ID, name, dept, email, salary, skills, joinDate);
    }
}

class EmployeeService {
    private static final String DB_NAME = "employees";
    private static final String EMP_COLL = "emp";
    private static final String CONN_STRING = "mongodb://localhost:27017/";

    private final MongoClient mongoClient;
    private final MongoCollection<Document> emp;
    private final Scanner scan = new Scanner(System.in);
    private String read(String prompt) {
        System.out.print(prompt + ": ");
        return scan.nextLine().trim();
    }

    public EmployeeService() {
        this.mongoClient = MongoClients.create(CONN_STRING); // Open client once
        MongoDatabase database = mongoClient.getDatabase(DB_NAME);
        this.emp = database.getCollection(EMP_COLL);

        Runtime.getRuntime().addShutdownHook(new Thread(mongoClient::close));
    }

    public void addEmployee() {
        String email = read("Enter Email");
        if (emp.find(eq("EMP_Email", email)).first() != null) {
            System.out.println("Error: Email already exists.");
            return;
        }

        Document doc = new Document()
                .append("EMP_ID", UUID.randomUUID().toString())
                .append("EMP_Name", read("Name"))
                .append("EMP_Dept", read("Department"))
                .append("EMP_Email", email)
                .append("EMP_Salary", Integer.parseInt(read("Salary")))
                .append("EMP_Skills", Arrays.stream(read("Skills (comma-separated)").split(","))
                        .map(String::trim)
                        .toList())
                .append("EMP_JoinDate", LocalDate.parse(read("Join Date (YYYY-MM-DD)")).toString());

        emp.insertOne(doc);
        System.out.println("Employee added.");
    }

    public void updateEmployee() {
        String email = read("Employee Email (for update)");
        Document empDoc = emp.find(eq("EMP_Email", email)).first();
        if (empDoc == null) {
            System.out.println("No employee found.");
            return;
        }

        Document update = new Document();
        String val;

        val = read("New Name (or '-' to skip)");
        if (!val.equals("-")) update.append("EMP_Name", val);

        val = read("New Department (or '-')");
        if (!val.equals("-")) update.append("EMP_Dept", val);

        val = read("New Email (or '-' to skip)");
        if (!val.equals("-")) update.append("EMP_Email", val);

        val = read("New Salary (or '-')");
        if (!val.equals("-")) update.append("EMP_Salary", Integer.parseInt(val));

        val = read("New Skills (comma-separated or '-')");
        if (!val.equals("-")) update.append("EMP_Skills", Arrays.asList(val.split(",")));

        val = read("New Join Date (YYYY-MM-DD or '-')");
        if (!val.equals("-")) update.append("EMP_JoinDate", LocalDate.parse(val).toString());

        if (!update.isEmpty()) {
            emp.updateOne(eq("EMP_Email", email), new Document("$set", update));
            System.out.println("Employee updated.");
        }
    }

    public void deleteEmployee() {
        String choice = read("Delete by (1) Email or (2) ID?");
        DeleteResult result = null;
        if (choice.equals("1")) {                                                 // for Email
            result = emp.deleteOne(eq("EMP_Email", read("Enter Email")));
        } else if (choice.equals("2")) {                                          // for ID
            result = emp.deleteOne(eq("_id", new ObjectId(read("Enter MongoDB _id"))));
        }
        if (result != null && result.getDeletedCount() > 0) {
            System.out.println("Deleted successfully.");
        } else {
            System.out.println("No record deleted.");
        }
    }

    public void searchEmployees() {
        System.out.println("Search by: (1) Name, (2) Department, (3) Skill, (4) Joining Date Range");
        String choice = scan.nextLine();
        FindIterable<Document> results = null;

        switch (choice) {
            case "1":
                results = emp.find(regex("EMP_Name", read("Enter part of name"), "i"));
                break;
            case "2":
                results = emp.find(eq("EMP_Dept", read("Department")));
                break;
            case "3":
                results = emp.find(elemMatch("EMP_Skills", eq(read("Skill"))));
                break;
            case "4":
                LocalDate from = LocalDate.parse(read("From Date (YYYY-MM-DD)"));
                LocalDate to = LocalDate.parse(read("To Date (YYYY-MM-DD)"));
                results = emp.find(and(
                        gte("EMP_JoinDate", from.toString()),
                        lte("EMP_JoinDate", to.toString())));
                break;
            default:
                System.out.println("Invalid option.");
        }

        if (results != null) {
            for (Document d : results) System.out.println(d.toJson());
        }
    }

    public void listWithPagination() {
        int page = Integer.parseInt(read("Page number"));
        int limit = Integer.parseInt(read("Results per page"));
        String sortBy = read("Sort by (EMP_Name / EMP_JoinDate)");

        FindIterable<Document> results = emp.find()
                .sort(ascending(sortBy))
                .skip((page - 1) * limit)
                .limit(limit);

        for (Document d : results) System.out.println(Arrays.toString(d.toJson().split("\n")));
    }

    public void departmentStatistics() {
        AggregateIterable<Document> stats = emp.aggregate(List.of(
                Aggregates.group("$EMP_Dept", Accumulators.sum("count", 1))
        ));
        for (Document doc : stats) System.out.println(doc.toJson());
    }
}


public class Main {
    public static void main(String[] args) {

        // === STEP 1: Redirect System.err to capture Mongo logs ===
        PrintStream originalErr = System.err;
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errBuffer));  // Capture SLF4J output

        EmployeeService service = new EmployeeService();
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.println("\n--- EMPLOYEE MANAGEMENT PORTAL ---");
            System.out.println("1. Add Employee");
            System.out.println("2. Update Employee");
            System.out.println("3. Delete Employee");
            System.out.println("4. Search Employees");
            System.out.println("5. List Employees with Pagination");
            System.out.println("6. Department Statistics");
            System.out.println("7. Exit");
            System.out.print("Choose: ");
            System.out.flush();  // Ensure flush before Mongo logs appear
            String choice = sc.nextLine();
            switch (choice) {
                case "1": service.addEmployee(); break;
                case "2": service.updateEmployee(); break;
                case "3": service.deleteEmployee(); break;
                case "4": service.searchEmployees(); break;
                case "5": service.listWithPagination(); break;
                case "6": service.departmentStatistics(); break;
                case "7":
                    System.out.println("Shutting down MongoDB client...");
                    System.out.println();
                    System.out.println("Mongo Logs through SLF4J(redirected, buffered, and manually delayed 'flush' of LOGS, delayed till program exits from execution) :");
                    System.out.println("-".repeat(140));
                    // === STEP 2: Flush Mongo logs captured earlier ===
                    System.setErr(originalErr);  // Restore original err stream
                    System.out.println(errBuffer.toString());  // Print all buffered Mongo logs
                    return;
                default: System.out.println("Invalid option.");
            }
        }
    }
}