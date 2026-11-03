package org.example.survey.model;

import java.util.ArrayList;
import java.util.List;

public class Page {

    private List<Survey> items = new ArrayList<>();
    private int page;
    private int totalPages;
    private long total;
    private String query = "";

    public List<Survey> getItems() {
        return items;
    }

    public void setItems(List<Survey> items) {
        this.items = items;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public boolean isHasPrevious() {
        return page > 1;
    }

    public boolean isHasNext() {
        return page < totalPages;
    }

}
