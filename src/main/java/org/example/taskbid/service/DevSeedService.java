package org.example.taskbid.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.example.taskbid.dto.DevSeedResponse;
import org.example.taskbid.entity.Profile;
import org.example.taskbid.entity.Skill;
import org.example.taskbid.entity.Task;
import org.example.taskbid.entity.User;
import org.example.taskbid.entity.enums.Roles;
import org.example.taskbid.entity.enums.TaskStatus;
import org.example.taskbid.repositiry.ProfileRepository;
import org.example.taskbid.repositiry.SkillRepository;
import org.example.taskbid.repositiry.TaskRepository;
import org.example.taskbid.repositiry.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ConditionalOnProperty(name = "app.dev-seed.enabled", havingValue = "true", matchIfMissing = true)
public class DevSeedService {

    public static final String CUSTOMER_EMAIL = "demo.customer@taskbid.local";
    public static final String EXECUTOR_ONE_EMAIL = "demo.cleaner@taskbid.local";
    public static final String EXECUTOR_TWO_EMAIL = "demo.tech@taskbid.local";
    public static final String PASSWORD = "password123";
    public static final String DEV_TASK_PREFIX = "[DEV]";
    public static final int TARGET_CUSTOMERS = 8;
    public static final int TARGET_EXECUTORS = 16;
    public static final int TARGET_TASKS = 80;

    static List<String> CITIES = List.of("Минск", "Гомель", "Брест", "Гродно", "Витебск", "Могилёв");

    UserRepository userRepository;
    ProfileRepository profileRepository;
    SkillRepository skillRepository;
    TaskRepository taskRepository;
    PasswordEncoder passwordEncoder;

    @Transactional
    public DevSeedResponse seedRecommenderEpic() {
        Map<String, Skill> skills = skillRepository.findAll().stream()
                .collect(Collectors.toMap(Skill::getName, Function.identity()));

        int usersCreated = 0;
        List<Profile> customers = new ArrayList<>();
        List<Profile> executors = new ArrayList<>();

        SeedProfile customer = ensureProfile("demo_customer", CUSTOMER_EMAIL, Roles.CUSTOMER, "Минск",
                "Демо-заказчик для проверки recommender epic", List.of());
        usersCreated += customer.created() ? 1 : 0;
        customers.add(customer.profile());

        for (int index = 2; index <= TARGET_CUSTOMERS; index++) {
            SeedProfile seeded = ensureProfile(
                    "demo_customer_" + index,
                    "demo.customer" + index + "@taskbid.local",
                    Roles.CUSTOMER,
                    CITIES.get((index - 1) % CITIES.size()),
                    "Демо-заказчик #" + index,
                    List.of()
            );
            usersCreated += seeded.created() ? 1 : 0;
            customers.add(seeded.profile());
        }

        SeedProfile cleaner = ensureProfile("demo_cleaner", EXECUTOR_ONE_EMAIL, Roles.EXECUTOR, "Минск",
                "active selective: уборка, переезды и помощь по дому",
                skillList(skills, "Мытьё окон", "Генеральная уборка", "Подметание и мойка пола", "Помощь при переезде"));
        usersCreated += cleaner.created() ? 1 : 0;
        executors.add(cleaner.profile());

        SeedProfile tech = ensureProfile("demo_tech", EXECUTOR_TWO_EMAIL, Roles.EXECUTOR, "Гомель",
                "high quality: техника, Wi-Fi, компьютеры и мелкий ремонт",
                skillList(skills, "Настройка компьютеров", "Настройка Wi-Fi и оборудования", "Мелкий ремонт техники", "Электромонтаж"));
        usersCreated += tech.created() ? 1 : 0;
        executors.add(tech.profile());

        for (int index = 3; index <= TARGET_EXECUTORS; index++) {
            List<Skill> executorSkills = executorSkillBundle(skills, index);
            SeedProfile seeded = ensureProfile(
                    "demo_executor_" + index,
                    "demo.executor" + index + "@taskbid.local",
                    Roles.EXECUTOR,
                    CITIES.get((index + 1) % CITIES.size()),
                    "demo executor #" + index + " / " + behaviorName(index),
                    executorSkills
            );
            usersCreated += seeded.created() ? 1 : 0;
            executors.add(seeded.profile());
        }

        int existingDevTasks = (int) taskRepository.findAll().stream()
                .filter(task -> task.getTitle() != null && task.getTitle().startsWith(DEV_TASK_PREFIX))
                .count();
        int tasksToCreate = Math.max(0, TARGET_TASKS - existingDevTasks);
        List<Task> tasks = new ArrayList<>();
        for (int index = existingDevTasks + 1; index <= existingDevTasks + tasksToCreate; index++) {
            tasks.add(devTask(index, customers.get(index % customers.size()), skills));
        }
        if (!tasks.isEmpty()) {
            taskRepository.saveAll(tasks);
        }

        return DevSeedResponse.builder()
                .created(usersCreated > 0 || !tasks.isEmpty())
                .message("Dev recommender bulk seed is ready")
                .customerEmail(CUSTOMER_EMAIL)
                .executorOneEmail(EXECUTOR_ONE_EMAIL)
                .executorTwoEmail(EXECUTOR_TWO_EMAIL)
                .password(PASSWORD)
                .usersCreated(usersCreated)
                .tasksCreated(tasks.size())
                .applicationsCreated(0)
                .totalDemoUsers(customers.size() + executors.size())
                .totalDemoTasks(existingDevTasks + tasks.size())
                .build();
    }

    private SeedProfile ensureProfile(String username, String email, Roles role, String city, String description, List<Skill> skills) {
        User user = userRepository.findByEmail(email).orElse(null);
        boolean created = false;
        if (user == null) {
            user = userRepository.save(User.builder()
                    .username(username)
                    .email(email)
                    .password(passwordEncoder.encode(PASSWORD))
                    .createdAt(LocalDateTime.now())
                    .build());
            created = true;
        }

        Profile profile = profileRepository.findByUser(user).orElse(null);
        if (profile == null) {
            profile = profileRepository.save(Profile.builder()
                    .user(user)
                    .role(role)
                    .city(city)
                    .description(description)
                    .skills(skills)
                    .build());
            created = true;
        }

        return new SeedProfile(profile, created);
    }

    private Task devTask(int index, Profile author, Map<String, Skill> skills) {
        TaskTemplate template = taskTemplate(index, skills);
        return Task.builder()
                .author(author)
                .title(DEV_TASK_PREFIX + " " + template.title() + " #" + index)
                .description(template.description())
                .city(CITIES.get(index % CITIES.size()))
                .status(TaskStatus.OPEN)
                .createdAt(LocalDateTime.now().minusHours((index * 7L) % 240))
                .requiredSkills(template.skills())
                .build();
    }

    private TaskTemplate taskTemplate(int index, Map<String, Skill> skills) {
        return switch (index % 10) {
            case 0 -> new TaskTemplate("Вымыть окна и сделать уборку", "Окна, полы, пыль, кухня.",
                    skillList(skills, "Мытьё окон", "Генеральная уборка"));
            case 1 -> new TaskTemplate("Генеральная уборка квартиры", "Нужна качественная уборка после жильцов.",
                    skillList(skills, "Генеральная уборка", "Подметание и мойка пола"));
            case 2 -> new TaskTemplate("Помочь с переездом", "Коробки, мебель, аккуратная погрузка.",
                    skillList(skills, "Помощь при переезде", "Перевозка мебели"));
            case 3 -> new TaskTemplate("Настроить Wi-Fi и роутер", "Падает сеть, нужна настройка оборудования.",
                    skillList(skills, "Настройка Wi-Fi и оборудования", "Настройка компьютеров"));
            case 4 -> new TaskTemplate("Починить ноутбук", "Ноутбук шумит, перегревается, нужна диагностика.",
                    skillList(skills, "Мелкий ремонт техники", "Настройка компьютеров"));
            case 5 -> new TaskTemplate("Мелкий электромонтаж", "Розетка, выключатель, базовая проверка.",
                    skillList(skills, "Электромонтаж", "Мелкий ремонт техники"));
            case 6 -> new TaskTemplate("Собрать мебель", "Сборка шкафа и комода.",
                    skillList(skills, "Сборка мебели", "Малярные работы"));
            case 7 -> new TaskTemplate("Покосить траву", "Участок за городом, нужен инструмент.",
                    skillList(skills, "Покос травы", "Обрезка деревьев"));
            case 8 -> new TaskTemplate("Репетитор на вечер", "Подготовка школьника к контрольной.",
                    skillList(skills, "Репетиторство"));
            default -> new TaskTemplate("Курьерская доставка", "Доставить документы и небольшую посылку.",
                    skillList(skills, "Курьерская доставка", "Помощь при переезде"));
        };
    }

    private List<Skill> executorSkillBundle(Map<String, Skill> skills, int index) {
        return switch (index % 8) {
            case 0 -> skillList(skills, "Мытьё окон", "Генеральная уборка", "Уборка после ремонта", "Стирка и глажка");
            case 1 -> skillList(skills, "Перевозка мебели", "Курьерская доставка", "Помощь при переезде");
            case 2 -> skillList(skills, "Сборка мебели", "Ремонт квартир", "Малярные работы", "Плиточные работы");
            case 3 -> skillList(skills, "Настройка компьютеров", "Настройка Wi-Fi и оборудования", "Мелкий ремонт техники");
            case 4 -> skillList(skills, "Покос травы", "Посадка растений", "Обрезка деревьев", "Уборка снега");
            case 5 -> skillList(skills, "Электромонтаж", "Ремонт квартир", "Мелкий ремонт техники");
            case 6 -> skillList(skills, "Репетиторство", "Няня / сиделка", "Помощь пожилым");
            default -> skillList(skills, "Генеральная уборка", "Курьерская доставка", "Сборка мебели", "Настройка Wi-Fi и оборудования");
        };
    }

    private String behaviorName(int index) {
        return switch (index % 6) {
            case 0 -> "active selective";
            case 1 -> "active broad";
            case 2 -> "local only";
            case 3 -> "freshness-driven";
            case 4 -> "low activity";
            default -> "high quality";
        };
    }

    private List<Skill> skillList(Map<String, Skill> skills, String... names) {
        List<Skill> result = new ArrayList<>();
        for (String name : names) {
            Skill skill = skills.get(name);
            if (skill == null) {
                throw new IllegalStateException("Required seed skill is missing: " + name);
            }
            result.add(skill);
        }
        return result;
    }

    private record SeedProfile(Profile profile, boolean created) {
    }

    private record TaskTemplate(String title, String description, List<Skill> skills) {
    }
}
