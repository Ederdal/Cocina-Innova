package com.ordenaris

public class Constants {
    public static final String INCOME_DEFAULT_DAYS = "INCOME_DEFAULT_DAYS"
    public static final List<String> ALLOWED_INCOME_SORT_COLUMNS = ["lastUpdated", "dateCreated"]
    public static final List<String> ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp']

    public static final long MAX_SIZE = 2 * 1024 * 1024

    public static final Integer[] ALLOWED_PAGE_SIZES = [2, 5, 10, 20, 30]
    public static String VALID_EMAIL_DOMAINS = "VALID_EMAIL_DOMAINS"
    public static String CHEF_QUEUE_VISIBLE_LIMIT = "CHEF_QUEUE_VISIBLE_LIMIT"
    public static String CHEF_PREPARING_VISIBLE_LIMIT = "CHEF_PREPARING_VISIBLE_LIMIT"
    public static String CHEF_PREPARING_ACTIVE_LIMIT = "CHEF_PREPARING_ACTIVE_LIMIT"
    public static String CHEF_FINISHED_VISIBLE_LIMIT = "CHEF_FINISHED_VISIBLE_LIMIT"
    public static String CHEF_CANCELLED_VISIBLE_LIMIT = "CHEF_CANCELLED_VISIBLE_LIMIT"
    public static String APP_TIMEZONE = "APP_TIMEZONE"
    public static String CONSUME_CHART_DAYS = "CONSUME_CHART_DAYS"
    public static String REVIEW_STATUS_APPROVED = "REVIEW_STATUS_APPROVED"
    public static final String SALE_STATUS_PENDING = "Pending"
    public static final String SALE_STATUS_PAID = "Paid"

    static final String ROLE_ADMIN = "ROLE_ADMIN"
    static final String ROLE_FINANCE = "ROLE_FINANCE"
    static final String ROLE_CHEF = "ROLE_CHEF"

    static final String ADMIN = "admin"

}
