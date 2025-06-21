// Refined prompt interpretation:
// You want the following:
// 1. Option 5 and 6 output displayed in clean, user-friendly tabular or better format (not JSON).
// 2. ID fields (ObjectId / EMP_ID) should never be shown.
// 3. Output should be verbose, especially for department stats.
// 4. All inputs should be fault-tolerant: protect against crashes, invalid formats, SQL injection-like attacks, and user abuse.
// 5. Program should remain fully interactive, professional, defensive (not offensive), robust, and maintainable.

// All the necessary improvements will now be made directly in the code below.

// Updating only affected methods: listWithPagination() and departmentStatistics(),
// and adding secureInput() to sanitize all inputs.


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
import java.util.stream.Collectors;

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

    protected static boolean containsUnsafeChars(String input) {
        // Unsafe characters to match: < > ' " ; \ ` { } ( ) : % $ ! ^ = + ~
        return input.matches(".*[<>'\";\\\\`{}():%$!^=+~].*");
    }

    public static boolean isValidEmail(String email) {
        String emailRegex = "^(?![.])([a-zA-Z0-9_.-]+)(?<![.])@(?!(?:-))(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(?<!-)$";
        return email.matches(emailRegex);
    }


    // Helper method for secure, fault-tolerant input reading
    private String secureInput(String prompt) {
        while (true) {
            try {
                System.out.print(prompt + ": ");
                String input = scan.nextLine().trim();

                if (input.isEmpty()) {
                    System.out.println("Input cannot be empty. Please try again.");
                    continue;
                }

                // Basic validation against injections or control characters
                if (containsUnsafeChars(input)) {
                    System.out.println("Suspicious characters detected. Please enter valid input.");
                    continue;
                }

                // Auto-detect numeric fields from prompt text
                if (prompt.toLowerCase().matches(".*(page|number|salary|count|limit).*") && !input.matches("(\\d{1,3}(,\\d{3})*|\\d+|^\\s*-\\s*$)")
                ) {
                    System.out.println("Only numeric input allowed here. Please try again.");
                    continue;
                }

                return input;
            } catch (Exception e) {
                System.out.println("Unexpected error occurred. Please re-enter the input.");
            }
        }
    }

    public EmployeeService() {
        this.mongoClient = MongoClients.create(CONN_STRING); // Open client once
        MongoDatabase database = mongoClient.getDatabase(DB_NAME);
        this.emp = database.getCollection(EMP_COLL);

        Runtime.getRuntime().addShutdownHook(new Thread(mongoClient::close));
    }

    public void addEmployee() {
        String email = secureInput("Enter Email");
        if (!(isValidEmail(email))) {
            System.out.println("Email is not valid. Please enter valid Email ID :)");
            return;
        }

        if (emp.find(eq("EMP_Email", email)).first() != null) {
            System.out.println("Error: Email already exists.");
            return;
        }

        Document doc = new Document()
                .append("EMP_ID", UUID.randomUUID().toString())
                .append("EMP_Name", secureInput("Name"))
                .append("EMP_Dept", secureInput("Department"))
                .append("EMP_Email", email)
                .append("EMP_Salary", Integer.parseInt(secureInput("Salary")))
                .append("EMP_Skills", Arrays.stream(secureInput("Skills (comma-separated)").split(","))
                        .map(String::trim)
                        .toList())
                .append("EMP_JoinDate", LocalDate.parse(secureInput("Join Date (YYYY-MM-DD)")).toString());

        emp.insertOne(doc);
        System.out.println("Employee added.");
    }

    public void updateEmployee() {
        String email = secureInput("Employee Email (for update)");
        Document empDoc = emp.find(eq("EMP_Email", email)).first();
        if (empDoc == null) {
            System.out.println("No employee found.");
            return;
        }

        Document update = new Document();
        String val;

        val = secureInput("New Name (or '-' to skip)");
        if (!val.equals("-")) update.append("EMP_Name", val);

        val = secureInput("New Department (or '-')");
        if (!val.equals("-")) update.append("EMP_Dept", val);

        val = secureInput("New Email (or '-' to skip)");
        if (!(isValidEmail(email))) {
            System.out.println("Email is not valid. Please enter valid Email ID :)");
            val = "-";
        }
        if (!val.equals("-")) update.append("EMP_Email", val);

        val = secureInput("New Salary (or '-')");
        if (!val.equals("-")) update.append("EMP_Salary", Integer.parseInt(val));

        val = secureInput("New Skills (comma-separated or '-')");
        if (!val.equals("-")) update.append("EMP_Skills", Arrays.asList(val.split(",")));

        val = secureInput("New Join Date (YYYY-MM-DD or '-')");
        if (!val.equals("-")) update.append("EMP_JoinDate", LocalDate.parse(val).toString());

        if (!update.isEmpty()) {
            emp.updateOne(eq("EMP_Email", email), new Document("$set", update));
            System.out.println("Employee updated.");
        }
    }

    public void deleteEmployee() {
        String choice = secureInput("Delete by (1) Email or (2) ID?");
        DeleteResult result = null;
        if (choice.equals("1")) {
            String email = secureInput("Enter Email");
            if (containsUnsafeChars(email)) {
                System.out.println("Invalid input for deletion.");
                return;
            }
            // for Email
            result = emp.deleteOne(eq("EMP_Email", email));
        } else if (choice.equals("2")) {
            String emp_id = secureInput("Enter Email");
            if (containsUnsafeChars(emp_id)) {
                System.out.println("Invalid input for deletion.");
                return;
            }
            // for ID
            result = emp.deleteOne(eq("_id", new ObjectId(emp_id)));
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
                String nameQuery = secureInput("Enter part of name");
                if (containsUnsafeChars(nameQuery)) {
                    System.out.println("Search query contains unsafe characters.");
                    return;
                }
                results = emp.find(regex("EMP_Name", nameQuery, "i"));
                break;

            case "2":
                String deptQuery = secureInput("Department");
                if (containsUnsafeChars(deptQuery)) {
                    System.out.println("Search query contains unsafe characters.");
                    return;
                }
                results = emp.find(eq("EMP_Dept", deptQuery));
                break;

            case "3":
                String skiQuery = secureInput("Skill");
                if (containsUnsafeChars(skiQuery)) {
                    System.out.println("Search query contains unsafe characters.");
                    return;
                }
                results = emp.find(elemMatch("EMP_Skills", eq(skiQuery)));
                break;

            case "4":
                String fdateQuery = secureInput("From Date (YYYY-MM-DD)");
                if (containsUnsafeChars(fdateQuery)) {
                    System.out.println("Search query contains unsafe characters.");
                    return;
                }
                String tdateQuery = secureInput("To Date (YYYY-MM-DD)");
                if (containsUnsafeChars(tdateQuery)) {
                    System.out.println("Search query contains unsafe characters.");
                    return;
                }
                LocalDate from = LocalDate.parse(fdateQuery);
                LocalDate to = LocalDate.parse(tdateQuery);
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
        int page = Integer.parseInt(secureInput("Page number"));
        int limit = Integer.parseInt(secureInput("Results per page"));
        String sortChoice = secureInput("Sort by (1) Name / (2) Join Date");
        String sortBy;
        if (sortChoice.equals("1")) {
            sortBy = "EMP_Name";
        }
        else if (sortChoice.equals("2")) {
            sortBy = "EMP_JoinDate";
        }
        else {
            sortBy = "";
            System.out.println("Invalid input. Expected: '1' / '2'");
            return;
        }

        FindIterable<Document> results = emp.find()
                .sort(ascending(sortBy))
                .skip((page - 1) * limit)
                .limit(limit);

        if (!results.iterator().hasNext()) {
            System.out.println("No employee records found for this page.");
            return;
        }

        DisplayFormatter.printEmployeeRecordList(results);
    }

    public void departmentStatistics() {
        AggregateIterable<Document> stats = emp.aggregate(List.of(
                Aggregates.group("$EMP_Dept", Accumulators.sum("count", 1))
        ));

        System.out.println("\nDepartment-wise Summary of Employees:");
        System.out.println("=".repeat(50));
        System.out.printf("%-25s | %-20s\n", "Department", "Number of Employees");
        System.out.println("-".repeat(50));

        for (Document doc : stats) {
            System.out.printf("%-25s | %-20d\n",
                    doc.getString("_id"),
                    doc.getInteger("count"));
        }
        System.out.println("=".repeat(50));
    }
}

// New DisplayFormatter class to handle beautiful non-tabular aligned printing
class DisplayFormatter {
    public static void printEmployeeRecordList(FindIterable<Document> results) {
        int counter = 1;
        System.out.println("\n" + "=".repeat(120));
        for (Document d : results) {
            Object skillsObj = d.get("EMP_Skills");
            List<String> skillsList = new ArrayList<>();

            if (skillsObj instanceof List<?>) {
                List<?> rawList = (List<?>) skillsObj;
                boolean allStrings = rawList.stream().allMatch(item -> item instanceof String);
                if (allStrings) {
                    skillsList = rawList.stream().map(item -> (String) item).toList();
                }
            }

            System.out.printf("\nEmployee #%d\n", counter++);
            System.out.println("-".repeat(120));
            System.out.printf("%-15s : %s\n", "Name", d.getString("EMP_Name"));
            System.out.printf("%-15s : %s\n", "Department", d.getString("EMP_Dept"));
            System.out.printf("%-15s : %s\n", "Email", d.getString("EMP_Email"));
            System.out.printf("%-15s : $%,d\n", "Salary", d.getInteger("EMP_Salary"));
            System.out.printf("%-15s : %s\n", "Skills", String.join(", ", skillsList));
            System.out.printf("%-15s : %s\n", "Join Date", d.getString("EMP_JoinDate"));
            System.out.println("-".repeat(120));
        }
        System.out.println("=".repeat(120));
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