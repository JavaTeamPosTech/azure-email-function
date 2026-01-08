package com.postechfiap;


import com.azure.communication.email.*;
import com.azure.communication.email.models.*;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.core.util.polling.SyncPoller;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;


import java.util.Optional;
public class Function {

    @FunctionName("enviarEmail")
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.POST},
                    authLevel = AuthorizationLevel.FUNCTION
            )
            HttpRequestMessage<Optional<EmailDTO>> request,
            final ExecutionContext context) {

        context.getLogger().info("--- INÍCIO DO PROCESSAMENTO ---");

        EmailDTO dto = request.getBody().orElse(null);

        // LOG 1: Verificar se o JSON chegou corretamente
        if (dto != null) {
            context.getLogger().info("Payload recebido: Aluno=" + dto.getAluno() + ", Prioridade=" + dto.getPrioridade());
        } else {
            context.getLogger().warning("Payload está VAZIO ou nulo.");
        }

        if (dto == null || dto.getPrioridade() == null || dto.getAluno() == null) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Payload inválido: aluno e prioridade são obrigatórios.")
                    .build();
        }

        // 2. LOG 2: Verificar Variáveis de Ambiente (Onde está o seu erro atual)
        String endpoint = System.getenv("ACS_ENDPOINT");
        String senderEmail = System.getenv("SENDER_EMAIL");

        context.getLogger().info("Configurações lidas:");
        context.getLogger().info(">> ACS_ENDPOINT: " + (endpoint != null ? "CONFIGURADO" : "NULO"));
        context.getLogger().info(">> SENDER_EMAIL: " + (senderEmail != null ? senderEmail : "NULO (ERRO AQUI)"));

        try {
            context.getLogger().info("Criando cliente EmailClient...");
            EmailClient emailClient = new EmailClientBuilder()
                    .endpoint(endpoint)
                    .credential(new DefaultAzureCredentialBuilder().build())
                    .buildClient();

            if ("urgente".equalsIgnoreCase(dto.getPrioridade())) {
                context.getLogger().info("Executando lógica de envio de e-mail urgente...");

                EmailMessage message = new EmailMessage()
                        .setSenderAddress(senderEmail)
                        .setToRecipients(
                                new EmailAddress("admin@escola.com"),
                                new EmailAddress("franciscosouzalima@gmail.com"),
                                new EmailAddress("fuzetirafael@gmail.com"),
                                new EmailAddress("gustavosoaresbomfim@hotmail.com"),
                                new EmailAddress("fernanda_o_ferreira@hotmail.com")
                        )
                        .setSubject("ALERTA URGENTE: Feedback de " + dto.getAluno())
                        .setBodyPlainText("Detalhes do Feedback:\n" +
                                "Aluno: " + dto.getAluno() + "\n" +
                                "Nota: " + dto.getNota() + "\n" +
                                "Comentário: " + dto.getComentario());

                context.getLogger().info("Enviando para o ACS... (Aguardando poller)");
                SyncPoller<EmailSendResult, EmailSendResult> poller = emailClient.beginSend(message);
                poller.waitForCompletion();
                context.getLogger().info("Resposta do ACS recebida com sucesso.");
            } else {
                context.getLogger().info("Prioridade '" + dto.getPrioridade() + "' não disparou e-mail.");
            }

            return request.createResponseBuilder(HttpStatus.OK)
                    .body("Feedback processado com sucesso.")
                    .build();

        } catch (Exception e) {
            // LOG 3: Captura de erro detalhada
            context.getLogger().severe("FALHA CRÍTICA: " + e.getMessage());
            e.printStackTrace(); // Isso ajuda a ver a linha exata do erro no log

            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro interno ao processar e-mail: " + e.getMessage())
                    .build();
        }
    }

    /**
     * DTO para mapeamento do JSON recebido via POST
     */
    public static class EmailDTO {
        private String aluno;
        private String comentario;
        private String prioridade;
        private int nota;

        public String getAluno() { return aluno; }
        public void setAluno(String aluno) { this.aluno = aluno; }

        public String getComentario() { return comentario; }
        public void setComentario(String comentario) { this.comentario = comentario; }

        public String getPrioridade() { return prioridade; }
        public void setPrioridade(String prioridade) { this.prioridade = prioridade; }

        public int getNota() { return nota; }
        public void setNota(int nota) { this.nota = nota; }
    }
}