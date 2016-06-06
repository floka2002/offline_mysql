package com.floka.offline;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

public class App {

    private final Log log = LogFactory.getLog(getClass());

    private @Value("${mode}")
    String mode;

    private @Value("${file.path}")
    String inFile;

    private List<Map<String, Object>> cl;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    int count = 1;

    public static void main(String[] args) {
        ApplicationContext context
                = new ClassPathXmlApplicationContext("app-config.xml");
        App app = (App) context.getBean("task");
        app.readIn();
    }

    public void readIn() {
        if (mode.equals("real")) {
            log.info("Start programm in real mode");
        } else {
            log.info("Start programm in test mode");
        }

        try {
            BufferedReader br = new BufferedReader(new FileReader(inFile));
            String line = br.readLine();
            while (line != null) {
                runLine(line);
                line = br.readLine();
            }
            br.close();
        } catch (FileNotFoundException ex) {
            log.error(inFile + " не найден!");
        } catch (IOException ex) {
            log.error(inFile + " ошибка чтения!");
        }
    }

    void runLine(String line) {
        /*
        1) 105 - код оператора - tab_comm_operators 
        2) 405 - присоединение - tab_comm_operators_connections 
        3) 73432140025 - начало диапазона - start_range 
        4) 73432140026 - конец диапазона - end_range 
        5) 1- тип номера - tab_comm_operators_range_types 
        6) 20151001 - дата начала действия нумерации - dte_start_date 
        7) 20991231 - дата окончания действия нумерации - dte_end_date 
        8) 1 - код дальности (топология) - tag_comm_operators_connections_matrix_topology 
        9) 1 - признак удалённого оператора - flg_farop;значения - 0 - нет;1 – да 
         */
        String in_tab_comm_operators;
        String in_tab_comm_operators_connections;
        String in_start_range;
        String in_end_range;
        String in_tab_comm_operators_range_types;
        String in_dte_start_date;
        String in_dte_end_date;
        String in_tag_comm_operators_connections_matrix_topology;
        String in_flg_farop;
        String ins_del;

        StringTokenizer st = new StringTokenizer(line, ",", false);
        if (st.countTokens() != 10) {
            log.info("Неверное число входных данных в строке " + (count++) + ":" + line);
            return;
        }
        in_tab_comm_operators = st.nextToken();
        in_tab_comm_operators_connections = st.nextToken();
        in_start_range = st.nextToken();
        in_end_range = st.nextToken();
        in_tab_comm_operators_range_types = st.nextToken();
        in_dte_start_date = st.nextToken();
        in_dte_end_date = st.nextToken();
        in_tag_comm_operators_connections_matrix_topology = st.nextToken();
        in_flg_farop = st.nextToken();
        ins_del = st.nextToken();

        if (ins_del.equals("1")) {

            log.info("Вводится нумерация, строка файла " + (count++) + " in_start_range=" + in_start_range + " in_end_range=" + in_end_range);

            String sql_tpd = "select cor.start_range, cor.end_range, cocm.dte_start_date, cocm.dte_end_date "
                    + "from comm_operators_range cor, comm_operators_connections_matrix cocm "
                    + "where cor.id = cocm.tab_comm_operators_range "
                    + "and cocm.tab_comm_operators_connections = ? "
                    + "and not (ifnull(cocm.dte_end_date,'20991231') < STR_TO_DATE(?, '%Y%m%d') "
                    + "or ifnull(cocm.dte_start_date,'20000101') > STR_TO_DATE(?, '%Y%m%d')) "
                    + "and not ( cor.end_range < ? or cor.start_range > ?)";

            cl = jdbcTemplate.queryForList(sql_tpd, new Object[]{in_tab_comm_operators_connections, in_dte_start_date, in_dte_end_date, in_start_range, in_end_range});
            if (!cl.isEmpty()) {
                log.info("Диапазон существует, присоединение ID=" + in_tab_comm_operators_connections + ":" + cl);
                return;
            }
            String sql_rang = "select id from comm_operators_range t where t.start_range = ? and t.end_range = ? and t.tab_comm_operators_range_types = ?";
            String id_rang = "";
            cl = jdbcTemplate.queryForList(sql_rang, in_start_range, in_end_range, in_tab_comm_operators_range_types);
            if (!cl.isEmpty()) {
                id_rang = cl.get(0).get("id").toString();
                log.info("Запись в rang существует № " + id_rang + " " + in_start_range + " " + in_end_range + " " + in_tab_comm_operators_range_types);
            } else if (mode.equals("real")) {
                jdbcTemplate.update("INSERT INTO comm_operators_range(start_range,end_range,tab_comm_operators_range_types) VALUES(?,?,?)",
                        in_start_range, in_end_range, in_tab_comm_operators_range_types);
                cl = jdbcTemplate.queryForList(sql_rang, in_start_range, in_end_range, in_tab_comm_operators_range_types);
                id_rang = cl.get(0).get("id").toString();
                log.info("Запись в rang введена № " + id_rang + " " + in_start_range + " " + in_end_range + " " + in_tab_comm_operators_range_types);
            }
            String sql_insert_matrix_on = "insert into comm_operators_connections_matrix ("
                    + "tab_comm_operators_connections,"
                    + "tab_comm_operators_range,"
                    + "dte_start_date,"
                    + "dte_end_date,"
                    + "tab_comm_operators_range_types,"
                    + "tag_comm_operators_connections_matrix_topology,"
                    + "flg_farop,"
                    + "gtg_comm_operators_connections_matrix_topology)"
                    + "values(?,?,?,?,?,?,'on','1')";
            String sql_insert_matrix_null = "insert into comm_operators_connections_matrix ("
                    + "tab_comm_operators_connections,"
                    + "tab_comm_operators_range,"
                    + "dte_start_date,"
                    + "dte_end_date,"
                    + "tab_comm_operators_range_types,"
                    + "tag_comm_operators_connections_matrix_topology,"
                    + "flg_farop,"
                    + "gtg_comm_operators_connections_matrix_topology)"
                    + "values(?,?,?,?,?,?,null,'1')";
            if (in_flg_farop.equals("1")) {
                jdbcTemplate.update(sql_insert_matrix_on, in_tab_comm_operators_connections, id_rang, in_dte_start_date, in_dte_end_date, in_tab_comm_operators_range_types,
                        in_tag_comm_operators_connections_matrix_topology);
            } else {
                jdbcTemplate.update(sql_insert_matrix_null, in_tab_comm_operators_connections, id_rang, in_dte_start_date, in_dte_end_date, in_tab_comm_operators_range_types,
                        in_tag_comm_operators_connections_matrix_topology);
            }
            log.info("Запись rang " + id_rang + "добавлена к присоединению ID=" + in_tab_comm_operators_connections);
        } else if (ins_del.equals("0")) {
            log.info("Удаляется нумерация, строка файла " + (count++) + " in_start_range=" + in_start_range + " in_end_range=" + in_end_range);
            String sql4del = "select cocm.id "
                    + "from comm_operators_range cor, comm_operators_connections_matrix cocm "
                    + "where cor.id = cocm.tab_comm_operators_range "
                    + "and cocm.tab_comm_operators_connections = ? "
                    + "and cocm.dte_start_date = STR_TO_DATE(?, '%Y%m%d') "
                    + "and cocm.dte_end_date = STR_TO_DATE(?, '%Y%m%d') "
                    + "and cor.start_range = ? and cor.end_range = ? ";
            cl = jdbcTemplate.queryForList(sql4del, new Object[]{in_tab_comm_operators_connections,
                in_dte_start_date, in_dte_end_date, in_start_range, in_end_range});
            if (cl.isEmpty()) {
                log.info("Диапазона, соответствующего строке " + (count - 1) + ", не существует, присоединение ID=" + in_tab_comm_operators_connections + ":");
                return;
            }
            if (mode.equals("real")) {
                log.info("Удаляется запись из comm_operators_connections_matrix" + cl);
                String sql_del = "delete from comm_operators_connections_matrix where id = ?";
                jdbcTemplate.update(sql_del, cl.get(0).get("id").toString());
            }
        }
    }
}
