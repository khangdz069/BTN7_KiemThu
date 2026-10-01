using Microsoft.VisualStudio.TestTools.UnitTesting;
using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace N7_btn
{
    [TestClass]
    public class Bai06
    {
        public TestContext TestContext { get; set; }

        [TestMethod]
        [DeploymentItem("data_csv\\Bai06.csv")]
        [DataSource("Microsoft.VisualStudio.TestTools.DataSource.CSV", "|DataDirectory|\\data_csv\\Bai06.csv", "Bai06#csv", DataAccessMethod.Sequential)]
        public void TinhTienDien()
        {
            MethodLibrary.MethodLibrary m = new MethodLibrary.MethodLibrary();

            string expStr = TestContext.DataRow["expected"].ToString();

            if (expStr.ToLower() == "exception")
            {
                try
                {
                    int c = Convert.ToInt32(TestContext.DataRow["c"]);
                    int m_val = Convert.ToInt32(TestContext.DataRow["m"]);

                    m.TinhTienDien(c, m_val);

                    Assert.Fail("Test case này mong đợi văng lỗi, nhưng chương trình lại chạy bình thường.");
                }
                catch (Exception)
                {
                }
            }
            else
            {
                int c = Convert.ToInt32(TestContext.DataRow["c"]);
                int m_val = Convert.ToInt32(TestContext.DataRow["m"]);
                double exp = Convert.ToDouble(expStr);

                double act = m.TinhTienDien(c, m_val);

                Assert.AreEqual(exp, act, 0.01);
            }
        }
    }
}